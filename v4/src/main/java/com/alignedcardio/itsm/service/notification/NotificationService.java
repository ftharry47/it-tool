package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.repository.NotificationRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JavaMailSender mailSender;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               SimpMessagingTemplate messagingTemplate,
                               JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.messagingTemplate = messagingTemplate;
        this.mailSender = mailSender;
    }

    @Transactional
    public Notification send(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setOrgId(request.orgId());
        notification.setUserId(request.userId());
        notification.setType(request.type());
        notification.setSubject(request.subject());
        notification.setBody(request.body());
        notification.setEntityType(request.entityType());
        notification.setEntityId(request.entityId());
        notification.setChannel(request.channel() == null ? Notification.Channel.BOTH : request.channel());
        notification.setInAppStatus(Notification.DeliveryStatus.PENDING);

        if (notification.getChannel() == Notification.Channel.EMAIL) {
            notification.setInAppStatus(null);
            notification.setEmailStatus(Notification.DeliveryStatus.PENDING);
        } else if (notification.getChannel() == Notification.Channel.IN_APP) {
            notification.setEmailStatus(null);
        } else {
            notification.setEmailStatus(Notification.DeliveryStatus.PENDING);
        }

        notification = notificationRepository.save(notification);

        NotificationPreference preference = preferenceRepository.findByUserId(request.userId())
                .orElse(defaultPreference(request.userId(), request.orgId()));

        if (notification.getChannel() == Notification.Channel.IN_APP
                || notification.getChannel() == Notification.Channel.BOTH) {
            deliverInApp(notification, preference);
        }

        if (notification.getChannel() == Notification.Channel.EMAIL
                || notification.getChannel() == Notification.Channel.BOTH) {
            queueOrSendEmail(notification, preference);
        }

        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> findUnreadByUser(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> n.getReadAt() == null)
                .toList();
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

    private void queueOrSendEmail(Notification notification, NotificationPreference preference) {
        if (!preference.isEmailEnabled()) {
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        if (preference.getDigestMode() != NotificationPreference.DigestMode.NONE) {
            // leave PENDING for the digest job
            return;
        }

        String address = preference.getEmailAddress();
        if (address == null || address.isBlank()) {
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(address);
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());

        try {
            mailSender.send(message);
            notification.setEmailStatus(Notification.DeliveryStatus.SENT);
        } catch (MailException e) {
            notification.setEmailStatus(Notification.DeliveryStatus.FAILED);
        }
    }

    NotificationPreference defaultPreference(UUID userId, UUID orgId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);
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
