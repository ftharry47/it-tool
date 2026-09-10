package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.repository.NotificationRepository;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class NotificationDigestJob implements Job {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final JavaMailSender mailSender;

    public NotificationDigestJob(NotificationRepository notificationRepository,
                                 NotificationPreferenceRepository preferenceRepository,
                                 JavaMailSender mailSender) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.mailSender = mailSender;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        sendPendingDigests(NotificationPreference.DigestMode.HOURLY);

        // Daily digest is sent once per hour so it does not need a separate job;
        // the caller can schedule a separate daily trigger if exact timing matters.
        sendPendingDigests(NotificationPreference.DigestMode.DAILY);
    }

    public void sendPendingDigests(NotificationPreference.DigestMode mode) {
        List<Notification> pending = notificationRepository.findByEmailStatusAndChannelIn(
                Notification.DeliveryStatus.PENDING,
                List.of(Notification.Channel.EMAIL, Notification.Channel.BOTH));

        Map<UUID, List<Notification>> byUser = pending.stream()
                .filter(n -> n.getUserId() != null)
                .collect(Collectors.groupingBy(Notification::getUserId));

        for (Map.Entry<UUID, List<Notification>> entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            List<Notification> userNotifications = entry.getValue();

            NotificationPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
            if (preference == null || !preference.isEmailEnabled() || preference.getDigestMode() != mode) {
                continue;
            }

            String address = preference.getEmailAddress();
            if (address == null || address.isBlank()) {
                continue;
            }

            StringBuilder body = new StringBuilder("You have the following notifications:\n\n");
            for (Notification n : userNotifications) {
                body.append("- ").append(n.getSubject()).append("\n");
            }

            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(address);
            message.setSubject("Notification digest (" + userNotifications.size() + ")");
            message.setText(body.toString());

            try {
                mailSender.send(message);
                for (Notification n : userNotifications) {
                    n.setEmailStatus(Notification.DeliveryStatus.SENT);
                    notificationRepository.save(n);
                }
            } catch (MailException e) {
                for (Notification n : userNotifications) {
                    n.setEmailStatus(Notification.DeliveryStatus.FAILED);
                    notificationRepository.save(n);
                }
            }
        }
    }
}
