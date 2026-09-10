package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationDigestJobTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void hourlyDigestBatchesAndSendsPendingEmailNotifications() {
        NotificationDigestJob job = new NotificationDigestJob(
                notificationRepository, preferenceRepository, mailSender);

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Notification n = new Notification();
        n.setOrgId(orgId);
        n.setUserId(userId);
        n.setType("TEST");
        n.setSubject("One");
        n.setBody("body");
        n.setChannel(Notification.Channel.EMAIL);
        n.setEmailStatus(Notification.DeliveryStatus.PENDING);

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setDigestMode(NotificationPreference.DigestMode.HOURLY);

        when(notificationRepository.findByEmailStatusAndChannelIn(
                Notification.DeliveryStatus.PENDING,
                List.of(Notification.Channel.EMAIL, Notification.Channel.BOTH)))
                .thenReturn(List.of(n));

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        job.sendPendingDigests(NotificationPreference.DigestMode.HOURLY);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("user@example.com", captor.getValue().getTo()[0]);
        assertTrue(captor.getValue().getSubject().contains("1"));

        assertEquals(Notification.DeliveryStatus.SENT, n.getEmailStatus());
        verify(notificationRepository).save(n);
    }
}
