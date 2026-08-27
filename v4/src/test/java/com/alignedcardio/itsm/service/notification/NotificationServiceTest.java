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
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void inAppSendPushesToCorrectUserSession() {
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, messagingTemplate, mailSender);

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.IN_APP);

        Notification result = service.send(request);

        assertEquals(userId, result.getUserId());
        assertEquals(Notification.DeliveryStatus.SENT, result.getInAppStatus());

        verify(messagingTemplate).convertAndSendToUser(
                eq(userId.toString()),
                eq("/queue/notifications"),
                any(NotificationPayload.class));
    }

    @Test
    void emailSendRespectsDigestMode() {
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, messagingTemplate, mailSender);

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setDigestMode(NotificationPreference.DigestMode.HOURLY);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.EMAIL);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.PENDING, result.getEmailStatus());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void immediateEmailIsSentWhenNoDigest() {
        NotificationService service = new NotificationService(
                notificationRepository, preferenceRepository, messagingTemplate, mailSender);

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.EMAIL);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.SENT, result.getEmailStatus());
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("user@example.com", captor.getValue().getTo()[0]);
    }
}
