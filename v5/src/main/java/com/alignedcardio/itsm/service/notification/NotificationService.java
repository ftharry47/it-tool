package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.repository.NotificationRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Action-required notification types that always send email immediately,
     * even when the user has digestMode HOURLY/DAILY. Informational types
     * (INCIDENT_UPDATE, INCIDENT_COMMENT, CREATED, FULFILLMENT_REMINDER)
     * remain digestible.
     */
    private static final java.util.Set<String> DIGEST_BYPASS_TYPES = java.util.Set.of(
            "INCIDENT_ASSIGNED", "INCIDENT_TIER_ESCALATED", "PROBLEM_ASSIGNED", "CHANGE_ASSIGNED",
            "FULFILLMENT_TASK_ASSIGNED", "TASK_ASSIGNED",
            "SLA_BREACH", "SLA_AT_RISK", "SLA_ESCALATION", "MENTION",
            "REJECTED", "PENDING_APPROVAL", "SR_SENT_TO_APPROVAL");

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Optional<JavaMailSender> mailSender;
    private final AppUserRepository appUserRepository;
    private final Optional<GraphMailClient> graphMailClient;
    private final PushService pushService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               SimpMessagingTemplate messagingTemplate,
                               Optional<JavaMailSender> mailSender,
                               AppUserRepository appUserRepository,
                               Optional<GraphMailClient> graphMailClient,
                               PushService pushService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailSender = mailSender;
        this.appUserRepository = appUserRepository;
        this.graphMailClient = graphMailClient;
        this.pushService = pushService;
    }

    @Transactional
    public Notification send(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setOrgId(request.orgId());
        notification.setUserId(request.userId());
        notification.setType(request.type());
        // In-app text is what the bell stores/shows; email and push get their
        // own fields from NotificationContent when present.
        NotificationContent content = request.content();
        notification.setSubject(content != null ? content.inAppSubject() : request.subject());
        notification.setBody(content != null ? content.inAppBody() : request.body());
        notification.setEntityType(request.entityType());
        notification.setEntityId(request.entityId());
        notification.setChannel(request.channel() == null ? Notification.Channel.BOTH : request.channel());
        notification.setInAppStatus(Notification.DeliveryStatus.PENDING);

        if (notification.getChannel() == Notification.Channel.EMAIL) {
            notification.setInAppStatus(null);
            notification.setEmailStatus(Notification.DeliveryStatus.PENDING);
        } else if (notification.getChannel() == Notification.Channel.IN_APP) {
            notification.setEmailStatus(null);
            notification.setPushStatus(Notification.DeliveryStatus.PENDING);
        } else {
            notification.setEmailStatus(Notification.DeliveryStatus.PENDING);
            notification.setPushStatus(Notification.DeliveryStatus.PENDING);
        }

        notification = notificationRepository.save(notification);

        NotificationPreference preference = preferenceRepository.findByUserId(request.userId())
                .orElse(defaultPreference(request.userId(), request.orgId()));

        if (!isNotificationTypeEnabled(preference, notification.getType())) {
            notification.setInAppStatus(Notification.DeliveryStatus.FAILED);
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return notificationRepository.save(notification);
        }

        if (notification.getChannel() == Notification.Channel.IN_APP
                || notification.getChannel() == Notification.Channel.BOTH) {
            deliverInApp(notification, preference);
        }

        if (notification.getChannel() == Notification.Channel.EMAIL
                || notification.getChannel() == Notification.Channel.BOTH) {
            queueOrSendEmail(notification, preference, content);
        }

        if (notification.getChannel() == Notification.Channel.IN_APP
                || notification.getChannel() == Notification.Channel.BOTH) {
            deliverPush(notification, preference, content);
        }

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> findUnreadByUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Notification> findByUser(UUID userId, boolean unreadOnly, Integer limit) {
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (unreadOnly) {
            notifications = notifications.stream()
                    .filter(n -> n.getReadAt() == null)
                    .toList();
        }
        if (limit != null && limit > 0 && notifications.size() > limit) {
            notifications = notifications.subList(0, limit);
        }
        return notifications;
    }

    @Transactional(readOnly = true)
    public long countUnreadByUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .count();
    }

    @Transactional
    public void markRead(UUID notificationId, UUID userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new com.alignedcardio.itsm.service.NotFoundException("Notification not found"));
        if (!notification.getUserId().equals(userId)) {
            throw new com.alignedcardio.itsm.service.NotFoundException("Notification not found");
        }
        notification.setReadAt(OffsetDateTime.now());
        notification.setInAppStatus(Notification.DeliveryStatus.SENT);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        List<Notification> unread = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
        for (Notification notification : unread) {
            notification.setReadAt(OffsetDateTime.now());
            notification.setInAppStatus(Notification.DeliveryStatus.SENT);
            notificationRepository.save(notification);
        }
    }

    private void deliverInApp(Notification notification, NotificationPreference preference) {
        if (!preference.isInAppEnabled()) {
            notification.setInAppStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        messagingTemplate.convertAndSendToUser(
                notification.getUserId().toString(),
                "/queue/notifications",
                toPayload(notification));

        notification.setInAppStatus(Notification.DeliveryStatus.SENT);
    }

    private void queueOrSendEmail(Notification notification, NotificationPreference preference,
                                  NotificationContent content) {
        if (!preference.isEmailEnabled()) {
            logger.warn("Email disabled for user {}, marking FAILED", notification.getUserId());
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        if (preference.getDigestMode() != NotificationPreference.DigestMode.NONE
                && !DIGEST_BYPASS_TYPES.contains(notification.getType())) {
            // leave PENDING for the digest job
            logger.info("Digest mode {} for user {}, email left PENDING", preference.getDigestMode(), notification.getUserId());
            return;
        }

        String address = preference.getEmailAddress();
        if (address == null || address.isBlank()) {
            address = appUserRepository.findById(notification.getUserId())
                    .map(AppUser::getEmail)
                    .orElse(null);
        }
        if (address == null || address.isBlank()) {
            logger.warn("No email address resolved for user {}, marking FAILED", notification.getUserId());
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        String subject = content != null ? content.emailSubject() : notification.getSubject();
        String body = content != null ? content.emailBody() : notification.getBody();
        String htmlBody = content != null ? content.emailHtmlBody() : null;
        boolean isHtml = htmlBody != null && !htmlBody.isBlank();

        if (graphMailClient.isPresent()) {
            logger.info("Sending Graph email to {} for notification {}", address, notification.getId());
            try {
                graphMailClient.get().sendEmail(address, subject, isHtml ? htmlBody : body, isHtml);
                notification.setEmailStatus(Notification.DeliveryStatus.SENT);
                logger.info("Graph email SENT for notification {}", notification.getId());
            } catch (Exception e) {
                logger.error("Graph email FAILED for notification {}", notification.getId(), e);
                notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            }
            return;
        }

        if (!mailSender.isPresent()) {
            logger.warn("No JavaMailSender configured; skipping SMTP email for notification {}", notification.getId());
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        logger.info("Sending SMTP email to {} for notification {}", address, notification.getId());
        if (isHtml) {
            try {
                jakarta.mail.internet.MimeMessage message = mailSender.get().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(address);
                helper.setSubject(subject);
                helper.setText(body, htmlBody);
                mailSender.get().send(message);
                notification.setEmailStatus(Notification.DeliveryStatus.SENT);
                logger.info("SMTP HTML email SENT for notification {}", notification.getId());
            } catch (Exception e) {
                logger.error("SMTP HTML email FAILED for notification {}", notification.getId(), e);
                notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            }
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(address);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.get().send(message);
            notification.setEmailStatus(Notification.DeliveryStatus.SENT);
            logger.info("SMTP email SENT for notification {}", notification.getId());
        } catch (MailException e) {
            logger.error("SMTP email FAILED for notification {}", notification.getId(), e);
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
        }
    }

    private void deliverPush(Notification notification, NotificationPreference preference,
                             NotificationContent content) {
        if (!preference.isPushEnabled()) {
            notification.setPushStatus(null);
            return;
        }
        try {
            String url = pushUrl(notification);
            String title = content != null ? content.pushTitle() : notification.getSubject();
            String body = content != null ? content.pushBody() : notification.getBody();
            boolean sent = pushService.sendToUser(notification.getUserId(), title, body, url);
            notification.setPushStatus(sent
                    ? Notification.DeliveryStatus.SENT
                    : Notification.DeliveryStatus.FAILED);
        } catch (Exception e) {
            logger.error("Push delivery failed for notification {}", notification.getId(), e);
            notification.setPushStatus(Notification.DeliveryStatus.FAILED);
        }
    }

    /**
     * Builds a click-through URL that matches a real frontend route.
     * Staff roles land on /dashboard/*, end users on /home/*.
     */
    private String pushUrl(Notification notification) {
        if (notification.getEntityId() == null || notification.getEntityType() == null) {
            return "/";
        }
        boolean staff = appUserRepository.findById(notification.getUserId())
                .map(u -> u.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .anyMatch(r -> List.of("AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN").contains(r)))
                .orElse(false);
        String prefix = staff ? "/dashboard" : "/home";
        return switch (notification.getEntityType().toUpperCase()) {
            case "INCIDENT" -> prefix + "/incidents/" + notification.getEntityId();
            case "SERVICE_REQUEST" -> prefix + "/service-requests/" + notification.getEntityId();
            case "LOCATION" -> "/admin/locations";
            default -> "/";
        };
    }

    private boolean isNotificationTypeEnabled(NotificationPreference preference, String type) {
        if (type == null) {
            return true;
        }
        String t = type.toUpperCase();
        if (t.endsWith("_ASSIGNED")) {
            return preference.isNotifyAssignment();
        }
        return switch (t) {
            case "INCIDENT_UPDATE", "INCIDENT_PRIORITY_CHANGED", "INCIDENT_TIER_ESCALATED" -> preference.isNotifyStatusChange();
            case "INCIDENT_COMMENT", "SR_COMMENT" -> preference.isNotifyComment();
            case "MENTION" -> preference.isNotifyMention();
            default -> true;
        };
    }

    NotificationPreference defaultPreference(UUID userId, UUID orgId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);
        preference.setNotifyStatusChange(true);
        preference.setNotifyAssignment(true);
        preference.setNotifyComment(true);
        preference.setNotifyMention(true);
        return preference;
    }

    private NotificationPayload toPayload(Notification notification) {
        return new NotificationPayload(
                notification.getId(),
                notification.getType(),
                notification.getSubject(),
                notification.getBody(),
                notification.getEntityType(),
                notification.getEntityId(),
                Optional.ofNullable(notification.getCreatedAt())
                        .map(t -> t.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                        .orElse(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)));
    }
}
