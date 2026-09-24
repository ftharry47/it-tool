package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.CommentCreateRequest;
import com.alignedcardio.itsm.api.incident.IncidentCommentResponse;
import com.alignedcardio.itsm.api.servicerequest.ServiceRequestCommentResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CommentAuthorType;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentComment;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.ServiceRequestComment;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.IncidentCommentRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.IncidentWatcherRepository;
import com.alignedcardio.itsm.repository.ServiceRequestCommentRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for internal-comment visibility. The activity-timeline
 * "Public comment" mislabel was a frontend field-name bug (isInternal vs
 * isPublic); these tests prove the actual exposure boundary: internal comment
 * bodies must never reach an END_USER response, on incidents or requests.
 */
@ExtendWith(MockitoExtension.class)
class CommentVisibilityTest {

    @Mock private IncidentRepository incidentRepository;
    @Mock private IncidentCommentRepository incidentCommentRepository;
    @Mock private IncidentWatcherRepository watcherRepository;
    @Mock private ServiceRequestRepository serviceRequestRepository;
    @Mock private ServiceRequestCommentRepository srCommentRepository;
    @Mock private FulfillmentTaskRepository fulfillmentTaskRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private SlaEngine slaEngine;
    @Mock private NotificationService notificationService;
    @Mock private NotificationTemplateBuilder notificationTemplateBuilder;
    @Mock private ApplicationEventPublisher eventPublisher;

    private IncidentCommentService incidentCommentService;
    private ServiceRequestCommentService srCommentService;

    private UUID orgId;
    private AppUser endUser;
    private AppUser agent;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        endUser = userWithRole("END_USER");
        agent = userWithRole("AGENT");
        incidentCommentService = new IncidentCommentService(
                incidentRepository, incidentCommentRepository, watcherRepository,
                appUserRepository, slaEngine, notificationService,
                notificationTemplateBuilder, eventPublisher);
        srCommentService = new ServiceRequestCommentService(
                serviceRequestRepository, srCommentRepository, fulfillmentTaskRepository,
                appUserRepository, notificationService, notificationTemplateBuilder);
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setDisplayName(roleName + " User");
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
        return user;
    }

    private Incident incident() {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setOrgId(orgId);
        incident.setRequester(endUser);
        return incident;
    }

    private IncidentComment incidentComment(Incident incident, boolean isPublic, String body) {
        IncidentComment c = new IncidentComment();
        c.setId(UUID.randomUUID());
        c.setIncident(incident);
        c.setAuthor(agent);
        c.setBody(body);
        c.setPublic(isPublic);
        c.setAuthorType(CommentAuthorType.USER);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    private ServiceRequest serviceRequest() {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(orgId);
        sr.setRequester(endUser);
        return sr;
    }

    private ServiceRequestComment srComment(ServiceRequest sr, boolean isPublic, String body) {
        ServiceRequestComment c = new ServiceRequestComment();
        c.setId(UUID.randomUUID());
        c.setServiceRequest(sr);
        c.setAuthor(agent);
        c.setBody(body);
        c.setPublic(isPublic);
        c.setAuthorType(CommentAuthorType.USER);
        c.setCreatedAt(OffsetDateTime.now());
        return c;
    }

    // --- Incident comments ---

    @Test
    void endUserNeverSeesInternalIncidentCommentBody() {
        Incident incident = incident();
        when(incidentRepository.findByOrgIdAndId(orgId, incident.getId())).thenReturn(Optional.of(incident));
        when(incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
                .thenReturn(List.of(
                        incidentComment(incident, true, "public reply"),
                        incidentComment(incident, false, "internal triage notes — requester must not see")));

        List<IncidentCommentResponse> result = incidentCommentService.listComments(orgId, incident.getId(), endUser);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isPublic());
        assertFalse(result.stream().anyMatch(c -> c.body().contains("internal triage notes")));
    }

    @Test
    void agentSeesInternalIncidentComments() {
        Incident incident = incident();
        when(incidentRepository.findByOrgIdAndId(orgId, incident.getId())).thenReturn(Optional.of(incident));
        when(incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
                .thenReturn(List.of(
                        incidentComment(incident, true, "public reply"),
                        incidentComment(incident, false, "internal triage notes")));

        List<IncidentCommentResponse> result = incidentCommentService.listComments(orgId, incident.getId(), agent);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> !c.isPublic()));
    }

    @Test
    void endUserCannotPostInternalIncidentComment() {
        Incident incident = incident();
        when(incidentRepository.findByOrgIdAndId(orgId, incident.getId())).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class,
                () -> incidentCommentService.addComment(orgId, incident.getId(), endUser,
                        new CommentCreateRequest("trying internal", false)));
    }

    // --- Service request comments ---

    @Test
    void endUserNeverSeesInternalServiceRequestCommentBody() {
        ServiceRequest sr = serviceRequest();
        when(serviceRequestRepository.findByOrgIdAndId(orgId, sr.getId())).thenReturn(Optional.of(sr));
        when(srCommentRepository.findByServiceRequestIdOrderByCreatedAtAsc(sr.getId()))
                .thenReturn(List.of(
                        srComment(sr, true, "public update"),
                        srComment(sr, false, "internal fulfillment note — requester must not see")));

        List<ServiceRequestCommentResponse> result = srCommentService.listComments(orgId, sr.getId(), endUser);

        assertEquals(1, result.size());
        assertTrue(result.get(0).isPublic());
        assertFalse(result.stream().anyMatch(c -> c.body().contains("internal fulfillment note")));
    }

    @Test
    void agentSeesInternalServiceRequestComments() {
        ServiceRequest sr = serviceRequest();
        when(serviceRequestRepository.findByOrgIdAndId(orgId, sr.getId())).thenReturn(Optional.of(sr));
        when(srCommentRepository.findByServiceRequestIdOrderByCreatedAtAsc(sr.getId()))
                .thenReturn(List.of(
                        srComment(sr, true, "public update"),
                        srComment(sr, false, "internal fulfillment note")));

        List<ServiceRequestCommentResponse> result = srCommentService.listComments(orgId, sr.getId(), agent);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(c -> !c.isPublic()));
    }

    @Test
    void endUserCannotPostInternalServiceRequestComment() {
        ServiceRequest sr = serviceRequest();
        when(serviceRequestRepository.findByOrgIdAndId(orgId, sr.getId())).thenReturn(Optional.of(sr));

        assertThrows(IllegalStateException.class,
                () -> srCommentService.addComment(orgId, sr.getId(), endUser,
                        new CommentCreateRequest("trying internal", false)));
    }

    // --- Mentions ---

    private com.alignedcardio.itsm.service.notification.NotificationContent stubContent() {
        return new com.alignedcardio.itsm.service.notification.NotificationContent(
                "subj", "plain", "<p>html</p>", "subj", "body", "push-title", "push-body");
    }

    @Test
    void serviceRequestMentionByDisplayNameNotifiesMentionedUser() {
        // Regression: SR comments had no mention support at all — only the
        // incident side triggered MENTION, and only on raw "@email".
        ServiceRequest sr = serviceRequest();
        AppUser mentioned = userWithRole("AGENT");
        mentioned.setDisplayName("Pat Fulfiller");
        mentioned.setEmail("pat.fulfiller@alignedcardio.com");
        when(serviceRequestRepository.findByOrgIdAndId(orgId, sr.getId())).thenReturn(Optional.of(sr));
        when(srCommentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(appUserRepository.findByOrgId(orgId)).thenReturn(List.of(endUser, agent, mentioned));
        when(notificationTemplateBuilder.forEvent(any(), any())).thenReturn(stubContent());
        when(appUserRepository.findById(any())).thenReturn(Optional.of(mentioned));

        srCommentService.addComment(orgId, sr.getId(), agent,
                new CommentCreateRequest("Handing off to @Pat Fulfiller for ordering", true));

        verify(notificationService).send(argThat((NotificationRequest r) ->
                r.userId().equals(mentioned.getId()) && "MENTION".equals(r.type())));
    }

    @Test
    void incidentMentionByDisplayNameNotifiesMentionedUser() {
        // The old regex only matched "@token" against the email column —
        // "@Pat Fulfiller" (what the picker inserts) never resolved.
        Incident incident = incident();
        incident.setNumber(42L);
        incident.setTitle("Test incident");
        AppUser mentioned = userWithRole("AGENT");
        mentioned.setDisplayName("Pat Fulfiller");
        mentioned.setEmail("pat.fulfiller@alignedcardio.com");
        when(incidentRepository.findByOrgIdAndId(orgId, incident.getId())).thenReturn(Optional.of(incident));
        when(incidentCommentRepository.save(any())).thenAnswer(i -> {
            IncidentComment c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(incidentCommentRepository.findByIncidentIdOrderByCreatedAtAsc(incident.getId()))
                .thenReturn(List.of());
        when(watcherRepository.findByIncidentIdAndDeletedAtIsNull(incident.getId())).thenReturn(List.of());
        when(appUserRepository.findByOrgId(orgId)).thenReturn(List.of(endUser, agent, mentioned));
        when(notificationTemplateBuilder.forEvent(eq("MENTION"), any())).thenReturn(stubContent());

        incidentCommentService.addComment(orgId, incident.getId(), agent,
                new CommentCreateRequest("Looping in @Pat Fulfiller", true));

        verify(notificationService).send(argThat((NotificationRequest r) ->
                r.userId().equals(mentioned.getId()) && "MENTION".equals(r.type())));
    }

    @Test
    void internalMentionDoesNotNotifyEndUsers() {
        // A mention inside a work note must not leak to someone who can't see it.
        Incident incident = incident();
        when(incidentRepository.findByOrgIdAndId(orgId, incident.getId())).thenReturn(Optional.of(incident));
        when(incidentCommentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(appUserRepository.findByOrgId(orgId)).thenReturn(List.of(endUser, agent));

        incidentCommentService.addComment(orgId, incident.getId(), agent,
                new CommentCreateRequest("Internal note re @" + endUser.getDisplayName(), false));

        verify(notificationService, never()).send(argThat((NotificationRequest r) ->
                r.userId().equals(endUser.getId()) && "MENTION".equals(r.type())));
    }
}
