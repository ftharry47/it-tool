package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.NotificationPreference;
import com.alignedcardio.itsm.repository.AppUserRepository;
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

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private PushService pushService;

    private NotificationService service() {
        return new NotificationService(
                notificationRepository, preferenceRepository, messagingTemplate,
                Optional.of(mailSender), appUserRepository, Optional.empty(), pushService);
    }

    @Test
    void inAppSendPushesToCorrectUserSession() {
        NotificationService service = service();

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
        NotificationService service = service();

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
        NotificationService service = service();

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

    @Test
    void digestBypassTypeSendsImmediatelyEvenWithDailyDigest() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setDigestMode(NotificationPreference.DigestMode.DAILY);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // INCIDENT_ASSIGNED is action-required — must not wait for the daily digest.
        NotificationRequest request = new NotificationRequest(
                orgId, userId, "INCIDENT_ASSIGNED", "Subject", "Body", null, null, Notification.Channel.EMAIL);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.SENT, result.getEmailStatus());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void digestibleTypeStillQueuesWithDailyDigest() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setDigestMode(NotificationPreference.DigestMode.DAILY);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        // INCIDENT_UPDATE is informational — stays queued for the digest.
        NotificationRequest request = new NotificationRequest(
                orgId, userId, "INCIDENT_UPDATE", "Subject", "Body", null, null, Notification.Channel.EMAIL);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.PENDING, result.getEmailStatus());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void perChannelContentUsedWhenPresent() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setPushEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pushService.sendToUser(any(), anyString(), anyString(), anyString())).thenReturn(true);

        NotificationContent content = new NotificationContent(
                "EMAIL SUBJECT", "EMAIL BODY",
                "INAPP SUBJECT", "INAPP BODY",
                "PUSH TITLE", "PUSH BODY");

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "fallback", "fallback", "INCIDENT", entityId,
                Notification.Channel.BOTH, content);

        Notification result = service.send(request);

        // In-app stores the in-app variant.
        assertEquals("INAPP SUBJECT", result.getSubject());
        assertEquals("INAPP BODY", result.getBody());
        // Email got the email variant.
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertEquals("EMAIL SUBJECT", mail.getValue().getSubject());
        // Push got the push variant.
        verify(pushService).sendToUser(eq(userId), eq("PUSH TITLE"), eq("PUSH BODY"), anyString());
    }

    @Test
    void pushSkippedWhenPreferenceDisabled() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setPushEnabled(false);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.IN_APP);

        Notification result = service.send(request);

        assertNull(result.getPushStatus());
        verifyNoInteractions(pushService);
    }

    @Test
    void pushAttemptedWhenPreferenceEnabled() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setPushEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pushService.sendToUser(eq(userId), anyString(), anyString(), anyString())).thenReturn(true);

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.IN_APP);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.SENT, result.getPushStatus());
        verify(pushService).sendToUser(eq(userId), eq("Subject"), eq("Body"), anyString());
    }

    @Test
    void pushNotAttemptedForEmailOnlyChannel() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(false);
        preference.setEmailEnabled(true);
        preference.setEmailAddress("user@example.com");
        preference.setPushEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.EMAIL);

        Notification result = service.send(request);

        assertNull(result.getPushStatus());
        verifyNoInteractions(pushService);
    }

    @Test
    void pushFailureDoesNotAffectOtherChannels() {
        NotificationService service = service();

        UUID orgId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationPreference preference = new NotificationPreference();
        preference.setOrgId(orgId);
        preference.setUserId(userId);
        preference.setInAppEnabled(true);
        preference.setEmailEnabled(false);
        preference.setPushEnabled(true);
        preference.setDigestMode(NotificationPreference.DigestMode.NONE);

        when(preferenceRepository.findByUserId(userId)).thenReturn(Optional.of(preference));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pushService.sendToUser(any(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("push exploded"));

        NotificationRequest request = new NotificationRequest(
                orgId, userId, "TEST", "Subject", "Body", null, null, Notification.Channel.IN_APP);

        Notification result = service.send(request);

        assertEquals(Notification.DeliveryStatus.SENT, result.getInAppStatus());
        assertEquals(Notification.DeliveryStatus.FAILED, result.getPushStatus());
    }
}
