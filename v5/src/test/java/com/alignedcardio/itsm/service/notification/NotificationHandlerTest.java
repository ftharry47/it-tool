package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.event.ServiceRequestEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.NotificationPreferenceRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private NotificationPreferenceRepository preferenceRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final NotificationTemplateBuilder templateBuilder =
            new NotificationTemplateBuilder("http://localhost:8080");

    private NotificationHandler handler() {
        return new NotificationHandler(notificationService, appUserRepository,
                teamRepository, teamMemberRepository, templateBuilder, preferenceRepository);
    }

    @Test
    void sendNotificationActionRoutesToCorrectUser() throws Exception {
        NotificationHandler handler = handler();

        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        JsonNode action = objectMapper.readTree("""
                {
                  "userId": "%s",
                  "subject": "Assigned to you",
                  "body": "You have a new incident",
                  "channel": "in_app"
                }
                """.formatted(userId));

        IncidentCreatedEvent event = new IncidentCreatedEvent(
                orgId, incidentId, Map.of("status", "NEW"));

        handler.handle(event, action, UUID.randomUUID());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());

        NotificationRequest request = captor.getValue();
        assertEquals(userId, request.userId());
        assertEquals("Assigned to you", request.subject());
        assertEquals("You have a new incident", request.body());
        assertEquals(Notification.Channel.IN_APP, request.channel());
        assertEquals("INCIDENT", request.entityType());
        assertEquals(incidentId, request.entityId());
    }

    @Test
    void roleTargetedNotificationSendsToAllAgentPlusUsersInOrg() throws Exception {
        NotificationHandler handler = handler();

        UUID orgId = UUID.randomUUID();
        UUID srId = UUID.randomUUID();
        AppUser agent = user(UUID.randomUUID());
        AppUser teamLead = user(UUID.randomUUID());
        AppUser admin = user(UUID.randomUUID());

        when(appUserRepository.findByOrgIdAndRoleNames(eq(orgId),
                eq(List.of("AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN"))))
                .thenReturn(List.of(agent, teamLead, admin));

        JsonNode action = objectMapper.readTree("""
                {
                  "role": "AGENT",
                  "subject": "Request {{number}} ready to fulfill",
                  "body": "Approved request {{number}} is ready for fulfillment",
                  "channel": "IN_APP"
                }
                """);

        ServiceRequestEvent event = new ServiceRequestEvent(
                orgId, srId, "IN_FULFILLMENT", Map.of("number", "SR-42"));

        handler.handle(event, action, UUID.randomUUID());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(3)).send(captor.capture());

        List<UUID> recipients = captor.getAllValues().stream().map(NotificationRequest::userId).toList();
        assertTrue(recipients.containsAll(List.of(agent.getId(), teamLead.getId(), admin.getId())));
        captor.getAllValues().forEach(r -> {
            assertEquals(orgId, r.orgId());
            assertEquals("Request SR-42 ready to fulfill", r.subject());
        });
    }

    @Test
    void roleTargetedNotificationIsOrgScoped() throws Exception {
        NotificationHandler handler = handler();

        UUID orgId = UUID.randomUUID();
        // Repository is queried with the event's orgId - cross-org users are never returned
        when(appUserRepository.findByOrgIdAndRoleNames(eq(orgId), anyList()))
                .thenReturn(List.of(user(UUID.randomUUID())));

        JsonNode action = objectMapper.readTree("""
                {"role": "AGENT", "subject": "s", "body": "b", "channel": "IN_APP"}
                """);

        handler.handle(new ServiceRequestEvent(orgId, UUID.randomUUID(), "IN_FULFILLMENT", Map.of()),
                action, UUID.randomUUID());

        verify(appUserRepository).findByOrgIdAndRoleNames(eq(orgId),
                eq(List.of("AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN")));
        verify(notificationService, times(1)).send(any());
    }

    @Test
    void unknownRoleIsRejected() throws Exception {
        NotificationHandler handler = handler();

        JsonNode action = objectMapper.readTree("""
                {"role": "WIZARD", "subject": "s", "body": "b"}
                """);

        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new ServiceRequestEvent(UUID.randomUUID(), UUID.randomUUID(), "IN_FULFILLMENT", Map.of()),
                action, UUID.randomUUID()));
        verifyNoInteractions(notificationService);
    }

    @Test
    void teamTargetedNotificationSendsToTeamMembersOnly() throws Exception {
        NotificationHandler handler = handler();

        UUID orgId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID member1 = UUID.randomUUID();
        UUID member2 = UUID.randomUUID();
        UUID nonMember = UUID.randomUUID(); // AGENT+ but not on the team - must NOT be notified

        Team team = new Team();
        team.setId(teamId);
        team.setOrgId(orgId);
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId))
                .thenReturn(Optional.of(team));

        TeamMember m1 = new TeamMember();
        m1.setUserId(member1);
        TeamMember m2 = new TeamMember();
        m2.setUserId(member2);
        when(teamMemberRepository.findByTeamId(teamId)).thenReturn(List.of(m1, m2));

        JsonNode action = objectMapper.readTree("""
                {"teamId": "%s", "subject": "Request {{number}} ready", "body": "b", "channel": "IN_APP"}
                """.formatted(teamId));

        handler.handle(new ServiceRequestEvent(orgId, UUID.randomUUID(), "IN_FULFILLMENT",
                Map.of("number", "SR-7")), action, UUID.randomUUID());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService, times(2)).send(captor.capture());
        List<UUID> recipients = captor.getAllValues().stream().map(NotificationRequest::userId).toList();
        assertTrue(recipients.containsAll(List.of(member1, member2)));
        assertFalse(recipients.contains(nonMember));
        captor.getAllValues().forEach(r -> assertEquals(orgId, r.orgId()));
        verifyNoInteractions(appUserRepository);
    }

    @Test
    void teamTargetedNotificationRejectsCrossOrgTeam() throws Exception {
        NotificationHandler handler = handler();

        UUID orgId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId))
                .thenReturn(Optional.empty());

        JsonNode action = objectMapper.readTree("""
                {"teamId": "%s", "subject": "s", "body": "b"}
                """.formatted(teamId));

        assertThrows(IllegalArgumentException.class, () -> handler.handle(
                new ServiceRequestEvent(orgId, UUID.randomUUID(), "IN_FULFILLMENT", Map.of()),
                action, UUID.randomUUID()));
        verifyNoInteractions(notificationService);
    }

    private AppUser user(UUID id) {
        AppUser u = new AppUser();
        u.setId(id);
        return u;
    }
}
