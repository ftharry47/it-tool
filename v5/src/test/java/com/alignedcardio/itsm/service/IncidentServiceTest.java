package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.IncidentCreateRequest;
import com.alignedcardio.itsm.api.incident.IncidentLinkResponse;
import com.alignedcardio.itsm.api.incident.IncidentResponse;
import com.alignedcardio.itsm.api.incident.LinkCreateRequest;
import com.alignedcardio.itsm.api.auth.AuditLogResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.Category;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.IncidentComment;
import com.alignedcardio.itsm.entity.IncidentLink;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Priority;
import com.alignedcardio.itsm.entity.Role;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.entity.TeamMember;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.event.IncidentAssignedEvent;
import com.alignedcardio.itsm.event.IncidentPriorityChangedEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.CategoryRepository;
import com.alignedcardio.itsm.repository.IncidentCommentRepository;
import com.alignedcardio.itsm.repository.IncidentLinkRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.IncidentWatcherRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.PriorityRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import com.alignedcardio.itsm.repository.TimeEntryRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    private static final UUID ORG_ID = BaseEntity.DEFAULT_ORG_ID;

    @Mock private IncidentRepository incidentRepository;
    @Mock private PriorityRepository priorityRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private LocationRepository locationRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private TeamMemberRepository teamMemberRepository;
    @Mock private IncidentWatcherRepository watcherRepository;
    @Mock private IncidentLinkRepository linkRepository;
    @Mock private IncidentCommentRepository commentRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private EntityManager entityManager;
    @Mock private SlaEngine slaEngine;
    @Mock private SlaInstanceRepository slaInstanceRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private NotificationService notificationService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository,
                priorityRepository,
                categoryRepository,
                locationRepository,
                appUserRepository,
                teamRepository,
                teamMemberRepository,
                watcherRepository,
                linkRepository,
                commentRepository,
                auditLogRepository,
                entityManager,
                slaEngine,
                slaInstanceRepository,
                timeEntryRepository,
                notificationService,
                new com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder("http://localhost:8080"),
                eventPublisher,
                new ObjectMapper(),
                new AuditLogService(auditLogRepository, appUserRepository));

        lenient().when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(timeEntryRepository.findByEntityTypeAndEntityIdAndDeletedAtIsNull(anyString(), any()))
                .thenReturn(List.of());
        lenient().when(watcherRepository.findByIncidentIdAndDeletedAtIsNull(any())).thenReturn(List.of());

        // Default empty reloads for toResponse lazy-association lookups.
        // Tests that assert response fields override these for specific IDs.
        lenient().when(appUserRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(teamRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(locationRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(priorityRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(categoryRepository.findById(any(UUID.class))).thenReturn(Optional.empty());
    }

    @Test
    void firstAssignmentAllowsPriorityOverrideAndWritesAudit() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident incident = incident(null, priority("Medium", 3));
        Priority high = priority("High", 2);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));
        when(priorityRepository.findById(high.getId())).thenReturn(Optional.of(high));

        incidentService.assign(admin, ORG_ID, incident.getId(), assignee.getId(), high.getId());

        assertSame(assignee, incident.getAssignee());
        assertSame(high, incident.getPriority());
        assertEquals(Incident.Status.IN_PROGRESS, incident.getStatus());
        verify(slaEngine).onPriorityChanged(incident);
        verify(eventPublisher).publishEvent(any(IncidentAssignedEvent.class));
        verify(eventPublisher).publishEvent(any(IncidentPriorityChangedEvent.class));
        verify(notificationService).send(any(NotificationRequest.class));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertEquals("ASSIGN", auditLog.getAction());
        assertEquals("INCIDENT", auditLog.getEntityType());
        assertEquals(incident.getId(), auditLog.getEntityId());
        assertTrue(auditLog.getAfterState().contains("High"));
        assertTrue(auditLog.getAfterState().contains(assignee.getId().toString()));
    }

    @Test
    void reassignmentIsAllowedAndAuditedButPriorityOverrideIsRejected() {
        AppUser admin = userWithRole("ADMIN");
        AppUser oldAssignee = userWithRole("AGENT");
        AppUser newAssignee = userWithRole("AGENT");
        Incident incident = incident(oldAssignee, priority("Medium", 3));
        Priority high = priority("High", 2);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(newAssignee.getId())).thenReturn(Optional.of(newAssignee));

        assertThrows(IllegalStateException.class,
                () -> incidentService.assign(admin, ORG_ID, incident.getId(), newAssignee.getId(), high.getId()));
        verify(incidentRepository, never()).save(any(Incident.class));
        verify(auditLogRepository, never()).save(any(AuditLog.class));

        incidentService.assign(admin, ORG_ID, incident.getId(), newAssignee.getId(), null);

        assertSame(newAssignee, incident.getAssignee());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("REASSIGN", auditCaptor.getValue().getAction());
        assertTrue(auditCaptor.getValue().getBeforeState().contains(oldAssignee.getId().toString()));
        assertTrue(auditCaptor.getValue().getAfterState().contains(newAssignee.getId().toString()));
    }

    @Test
    void escalateRequiresAssignmentHigherPriorityAndReason() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident unassigned = incident(null, priority("Medium", 3));
        when(incidentRepository.findByOrgIdAndId(ORG_ID, unassigned.getId())).thenReturn(Optional.of(unassigned));

        assertThrows(IllegalStateException.class,
                () -> incidentService.escalate(admin, ORG_ID, unassigned.getId(), UUID.randomUUID(), "reason"));

        Incident assigned = incident(assignee, priority("High", 2));
        Priority low = priority("Low", 4);
        when(incidentRepository.findByOrgIdAndId(ORG_ID, assigned.getId())).thenReturn(Optional.of(assigned));
        when(priorityRepository.findById(low.getId())).thenReturn(Optional.of(low));

        assertThrows(IllegalStateException.class,
                () -> incidentService.escalate(admin, ORG_ID, assigned.getId(), low.getId(), "reason"));
        assertThrows(IllegalStateException.class,
                () -> incidentService.escalate(admin, ORG_ID, assigned.getId(), low.getId(), " "));
    }

    @Test
    void escalateUpdatesPriorityAndWritesCommentAuditEventAndNotifications() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident incident = incident(assignee, priority("Low", 4));
        Priority critical = priority("Critical", 1);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(priorityRepository.findById(critical.getId())).thenReturn(Optional.of(critical));

        incidentService.escalate(admin, ORG_ID, incident.getId(), critical.getId(), "Customer impact increased");

        assertSame(critical, incident.getPriority());
        verify(slaEngine).onPriorityChanged(incident);
        verify(eventPublisher).publishEvent(any(IncidentPriorityChangedEvent.class));
        verify(notificationService, atLeastOnce()).send(any(NotificationRequest.class));

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertEquals("ESCALATE_PRIORITY", auditLog.getAction());
        assertTrue(auditLog.getBeforeState().contains("Low"));
        assertTrue(auditLog.getAfterState().contains("Critical"));
        assertTrue(auditLog.getAfterState().contains("Customer impact increased"));
    }

    @Test
    void createWithLocationIdAssociatesLocation() {
        AppUser requester = userWithRole("END_USER");
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setOrgId(ORG_ID);
        category.setName("Hardware");
        Location location = new Location();
        location.setId(UUID.randomUUID());
        location.setOrgId(ORG_ID);
        location.setName("Richmond HQ");

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, location.getId()))
                .thenReturn(Optional.of(location));
        when(locationRepository.findById(location.getId())).thenReturn(Optional.of(location));
        when(priorityRepository.findByOrgIdAndName(ORG_ID, "Medium"))
                .thenReturn(Optional.of(priority("Medium", 3)));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident i = invocation.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setNumber(1L);
            return i;
        });

        IncidentCreateRequest request = new IncidentCreateRequest(
                "Laptop broken", "desc", 3, 3, category.getId(), null, location.getId(), null);
        IncidentResponse response = incidentService.create(requester, request);

        assertEquals(location.getId(), response.locationId());
        assertEquals("Richmond HQ", response.location());
    }

    @Test
    void createWithUnknownLocationIdThrowsNotFound() {
        AppUser requester = userWithRole("END_USER");
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setOrgId(ORG_ID);
        category.setName("Hardware");
        UUID missingLocationId = UUID.randomUUID();

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, missingLocationId))
                .thenReturn(Optional.empty());
        when(priorityRepository.findByOrgIdAndName(ORG_ID, "Medium"))
                .thenReturn(Optional.of(priority("Medium", 3)));

        IncidentCreateRequest request = new IncidentCreateRequest(
                "Laptop broken", "desc", 3, 3, category.getId(), null, missingLocationId, null);

        assertThrows(NotFoundException.class, () -> incidentService.create(requester, request));
        verify(incidentRepository, never()).saveAndFlush(any(Incident.class));
    }

    @Test
    void createFallsBackToFirstActivePriorityWhenMatrixNameMissing() {
        AppUser requester = userWithRole("END_USER");
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setOrgId(ORG_ID);
        category.setName("Hardware");
        Priority fallback = priority("P3", 1);

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(priorityRepository.findByOrgIdAndName(ORG_ID, "Medium")).thenReturn(Optional.empty());
        when(priorityRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(ORG_ID, Priority.Status.ACTIVE))
                .thenReturn(List.of(fallback));
        when(priorityRepository.findById(fallback.getId())).thenReturn(Optional.of(fallback));
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident i = invocation.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setNumber(1L);
            return i;
        });

        IncidentCreateRequest request = new IncidentCreateRequest(
                "Laptop broken", "desc", 3, 3, category.getId(), null, null, null);
        IncidentResponse response = incidentService.create(requester, request);

        assertEquals("P3", response.priority());
        verify(incidentRepository).saveAndFlush(any(Incident.class));
    }

    @Test
    void createSucceedsWithNullPriorityWhenOrgHasNoPriorities() {
        AppUser requester = userWithRole("END_USER");
        Category category = new Category();
        category.setId(UUID.randomUUID());
        category.setOrgId(ORG_ID);
        category.setName("Hardware");

        when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
        when(priorityRepository.findByOrgIdAndName(ORG_ID, "Medium")).thenReturn(Optional.empty());
        when(priorityRepository.findByOrgIdAndStatusOrderByDisplayOrderAsc(ORG_ID, Priority.Status.ACTIVE))
                .thenReturn(List.of());
        when(incidentRepository.saveAndFlush(any(Incident.class))).thenAnswer(invocation -> {
            Incident i = invocation.getArgument(0);
            i.setId(UUID.randomUUID());
            i.setNumber(1L);
            return i;
        });

        IncidentCreateRequest request = new IncidentCreateRequest(
                "Laptop broken", "desc", 3, 3, category.getId(), null, null, null);
        IncidentResponse response = incidentService.create(requester, request);

        assertNull(response.priority());
        verify(incidentRepository).saveAndFlush(any(Incident.class));
    }

    // --- Part A: tier tracking on assign + next-tier-only manual escalation ---

    private static final UUID TIER_L1 = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID TIER_L2 = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID TIER_L3 = UUID.fromString("00000000-0000-0000-0000-000000000022");

    private Team tierTeam(UUID id, String name) {
        Team team = new Team();
        team.setId(id);
        team.setOrgId(ORG_ID);
        team.setName(name);
        return team;
    }

    private TeamMember membership(UUID teamId, AppUser user) {
        TeamMember m = new TeamMember();
        m.setTeamId(teamId);
        m.setUserId(user.getId());
        m.setUser(user);
        return m;
    }

    @Test
    void assignSetsAssignmentTeamFromAssigneeTierMembership() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident incident = incident(null, priority("Medium", 3));
        Team l2 = tierTeam(TIER_L2, "L2 Support");

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));
        when(teamMemberRepository.findByUserId(assignee.getId()))
                .thenReturn(List.of(membership(TIER_L2, assignee)));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, TIER_L2))
                .thenReturn(Optional.of(l2));

        incidentService.assign(admin, ORG_ID, incident.getId(), assignee.getId(), null);

        assertSame(l2, incident.getAssignmentTeam());
    }

    @Test
    void assignClearsAssignmentTeamWhenAssigneeInNoTierTeam() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident incident = incident(null, priority("Medium", 3));
        incident.setAssignmentTeam(tierTeam(TIER_L1, "L1 Support"));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(assignee.getId())).thenReturn(Optional.of(assignee));
        when(teamMemberRepository.findByUserId(assignee.getId())).thenReturn(List.of());

        incidentService.assign(admin, ORG_ID, incident.getId(), assignee.getId(), null);

        assertNull(incident.getAssignmentTeam());
    }

    @Test
    void escalateTierBlockedWhenNoTierAssigned() {
        AppUser admin = userWithRole("ADMIN");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setAssignmentTeam(null);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.escalateTier(admin, ORG_ID, incident.getId(), "needs deeper investigation"));
        assertTrue(ex.getMessage().contains("support tier"));
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void escalateTierMovesToImmediateNextTierOnly() {
        AppUser admin = userWithRole("ADMIN");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setAssignmentTeam(tierTeam(TIER_L1, "L1 Support"));
        Team l2 = tierTeam(TIER_L2, "L2 Support");

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, TIER_L2))
                .thenReturn(Optional.of(l2));
        when(teamRepository.findById(TIER_L2)).thenReturn(Optional.of(l2));
        when(teamMemberRepository.findByTeamId(TIER_L2)).thenReturn(List.of());

        IncidentResponse response = incidentService.escalateTier(admin, ORG_ID, incident.getId(), "needs network team");

        assertSame(l2, incident.getAssignmentTeam());
        assertEquals(TIER_L2, response.assignmentTeamId());
        assertEquals("L2 Support", response.assignmentTeamName());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("ESCALATE_TIER", auditCaptor.getValue().getAction());
        assertTrue(auditCaptor.getValue().getAfterState().contains("needs network team"));
    }

    @Test
    void escalateTierClearsAssigneeAndFreezesOldAgent() {
        AppUser admin = userWithRole("ADMIN");
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setStatus(Incident.Status.IN_PROGRESS);
        incident.setAssignmentTeam(tierTeam(TIER_L1, "L1 Support"));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, TIER_L2))
                .thenReturn(Optional.of(tierTeam(TIER_L2, "L2 Support")));
        when(teamMemberRepository.findByTeamId(TIER_L2)).thenReturn(List.of());

        incidentService.escalateTier(admin, ORG_ID, incident.getId(), "needs L2");

        // Assignee cleared — ticket visibly needs a new owner in the new tier.
        assertNull(incident.getAssignee());

        // The old agent can no longer transition status (not assignee, not admin).
        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agent, ORG_ID, incident.getId(), Incident.Status.RESOLVED, null));

        // Admin retains full access on the same escalated incident.
        incidentService.updateStatus(admin, ORG_ID, incident.getId(), Incident.Status.RESOLVED, null);
        assertEquals(Incident.Status.RESOLVED, incident.getStatus());
    }

    @Test
    void escalateTierBlockedAtHighestTier() {
        AppUser admin = userWithRole("ADMIN");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setAssignmentTeam(tierTeam(TIER_L3, "L3 Support"));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.escalateTier(admin, ORG_ID, incident.getId(), "further"));
        assertTrue(ex.getMessage().contains("highest support tier"));
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void escalateTierRequiresReason() {
        AppUser admin = userWithRole("ADMIN");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setAssignmentTeam(tierTeam(TIER_L1, "L1 Support"));

        assertThrows(IllegalStateException.class,
                () -> incidentService.escalateTier(admin, ORG_ID, incident.getId(), "  "));
    }

    @Test
    void priorityEscalationDoesNotAffectAssignmentTeam() {
        AppUser admin = userWithRole("ADMIN");
        AppUser assignee = userWithRole("AGENT");
        Incident incident = incident(assignee, priority("Medium", 3));
        Team l1 = tierTeam(TIER_L1, "L1 Support");
        incident.setAssignmentTeam(l1);
        Priority high = priority("High", 2);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(priorityRepository.findById(high.getId())).thenReturn(Optional.of(high));

        incidentService.escalate(admin, ORG_ID, incident.getId(), high.getId(), "VIP impact");

        assertSame(l1, incident.getAssignmentTeam());
        assertSame(high, incident.getPriority());
    }

    // --- Part B: mandatory closing notes for CLOSED ---

    @Test
    void closingIncidentRequiresClosingNotes() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setStatus(Incident.Status.RESOLVED);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agent, ORG_ID, incident.getId(), Incident.Status.CLOSED, null));
        assertTrue(ex.getMessage().contains("Closing notes"));
        verify(incidentRepository, never()).save(any(Incident.class));

        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agent, ORG_ID, incident.getId(), Incident.Status.CLOSED, "   "));
    }

    @Test
    void closingIncidentWithNotesSucceeds() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setStatus(Incident.Status.RESOLVED);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IncidentResponse response = incidentService.updateStatus(
                agent, ORG_ID, incident.getId(), Incident.Status.CLOSED, "Replaced docking station; verified with user.");

        assertEquals(Incident.Status.CLOSED, incident.getStatus());
        assertEquals("Replaced docking station; verified with user.", incident.getClosingNotes());
        assertEquals("Replaced docking station; verified with user.", response.closingNotes());
        assertNotNull(incident.getClosedAt());
    }

    @Test
    void closingNotesHiddenFromEndUser() {
        AppUser endUser = userWithRole("END_USER");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setStatus(Incident.Status.CLOSED);
        incident.setClosingNotes("internal root cause details");

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IncidentResponse endUserView = incidentService.get(endUser, ORG_ID, incident.getId());
        assertNull(endUserView.closingNotes());

        AppUser agent = userWithRole("AGENT");
        IncidentResponse staffView = incidentService.get(agent, ORG_ID, incident.getId());
        assertEquals("internal root cause details", staffView.closingNotes());
    }

    // --- Item 1: assigned agent may escalate ---

    @Test
    void assignedAgentCanEscalatePriority() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Low", 4));
        Priority high = priority("High", 2);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(priorityRepository.findById(high.getId())).thenReturn(Optional.of(high));

        incidentService.escalate(agent, ORG_ID, incident.getId(), high.getId(), "impact grew");

        assertSame(high, incident.getPriority());
    }

    @Test
    void unassignedAgentCannotEscalate() {
        AppUser agent = userWithRole("AGENT");
        AppUser other = userWithRole("AGENT");
        Incident incident = incident(other, priority("Low", 4));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class,
                () -> incidentService.escalate(agent, ORG_ID, incident.getId(), UUID.randomUUID(), "reason"));
        assertThrows(IllegalStateException.class,
                () -> incidentService.escalateTier(agent, ORG_ID, incident.getId(), "reason"));
    }

    @Test
    void assignedAgentCanEscalateTierButTierRuleStillApplies() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setAssignmentTeam(null);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> incidentService.escalateTier(agent, ORG_ID, incident.getId(), "needs L2"));
        assertTrue(ex.getMessage().contains("support tier"));
    }

    // --- Item 2: reopen restricted to ADMIN/SUPER_ADMIN with mandatory comment ---

    @Test
    void reopenRequiresAdminAndComment() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setStatus(Incident.Status.CLOSED);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agent, ORG_ID, incident.getId(), Incident.Status.REOPENED, "reason"));

        AppUser admin = userWithRole("ADMIN");
        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(admin, ORG_ID, incident.getId(), Incident.Status.REOPENED, "  "));
        verify(incidentRepository, never()).save(any(Incident.class));
    }

    @Test
    void adminCanReopenWithComment() {
        AppUser admin = userWithRole("ADMIN");
        Incident incident = incident(userWithRole("AGENT"), priority("Medium", 3));
        incident.setStatus(Incident.Status.CLOSED);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        incidentService.updateStatus(admin, ORG_ID, incident.getId(), Incident.Status.REOPENED, "customer still affected");

        assertEquals(Incident.Status.REOPENED, incident.getStatus());
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        assertEquals("REOPEN", auditCaptor.getValue().getAction());
        assertTrue(auditCaptor.getValue().getAfterState().contains("customer still affected"));
    }

    // --- Item 3: one-time estimate, reset on reassignment/escalation/reopen ---

    @Test
    void estimateLocksAfterFirstSet() {
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        incidentService.setEstimatedMinutes(agent, ORG_ID, incident.getId(), 120);
        assertEquals(120, incident.getEstimatedMinutes());
        assertNotNull(incident.getEstimateSetAt());
        assertEquals(agent.getId(), incident.getEstimateSetById());

        assertThrows(IllegalStateException.class,
                () -> incidentService.setEstimatedMinutes(agent, ORG_ID, incident.getId(), 240));
    }

    @Test
    void estimateResetsOnReassignmentButNotNoOp() {
        AppUser admin = userWithRole("ADMIN");
        AppUser agentA = userWithRole("AGENT");
        AppUser agentB = userWithRole("AGENT");
        Incident incident = incident(agentA, priority("Medium", 3));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(agentA.getId())).thenReturn(Optional.of(agentA));
        when(appUserRepository.findById(agentB.getId())).thenReturn(Optional.of(agentB));

        incidentService.setEstimatedMinutes(agentA, ORG_ID, incident.getId(), 90);
        assertNotNull(incident.getEstimateSetAt());

        // No-op: same assignee re-confirmed -> lock must NOT reset
        incidentService.assign(admin, ORG_ID, incident.getId(), agentA.getId(), null);
        assertEquals(90, incident.getEstimatedMinutes());
        assertNotNull(incident.getEstimateSetAt());
        assertThrows(IllegalStateException.class,
                () -> incidentService.setEstimatedMinutes(agentA, ORG_ID, incident.getId(), 60));

        // Genuine reassignment -> new assignee gets a fresh estimate
        incidentService.assign(admin, ORG_ID, incident.getId(), agentB.getId(), null);
        assertNull(incident.getEstimatedMinutes());
        assertNull(incident.getEstimateSetAt());

        incidentService.setEstimatedMinutes(agentB, ORG_ID, incident.getId(), 45);
        assertEquals(45, incident.getEstimatedMinutes());
        assertEquals(agentB.getId(), incident.getEstimateSetById());
    }

    @Test
    void estimateResetsOnTierEscalationAndReopen() {
        AppUser admin = userWithRole("ADMIN");
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(agent, priority("Medium", 3));
        incident.setAssignmentTeam(tierTeam(TIER_L1, "L1 Support"));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, TIER_L2))
                .thenReturn(Optional.of(tierTeam(TIER_L2, "L2 Support")));
        when(teamMemberRepository.findByTeamId(TIER_L2)).thenReturn(List.of());

        incidentService.setEstimatedMinutes(agent, ORG_ID, incident.getId(), 60);
        incidentService.escalateTier(admin, ORG_ID, incident.getId(), "needs L2");
        assertNull(incident.getEstimateSetAt());
        // Escalation clears the assignee — the old agent loses assignee rights.
        assertNull(incident.getAssignee());

        // Reopen also resets (admin acts since the ticket is now unassigned)
        incidentService.setEstimatedMinutes(admin, ORG_ID, incident.getId(), 30);
        incident.setStatus(Incident.Status.CLOSED);
        incidentService.updateStatus(admin, ORG_ID, incident.getId(), Incident.Status.REOPENED, "still broken");
        assertNull(incident.getEstimateSetAt());
        assertNull(incident.getEstimatedMinutes());
    }

    @Test
    void nonAssigneeNonAdminCannotSetEstimate() {
        AppUser agent = userWithRole("AGENT");
        AppUser other = userWithRole("AGENT");
        Incident incident = incident(other, priority("Medium", 3));

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));

        assertThrows(IllegalStateException.class,
                () -> incidentService.setEstimatedMinutes(agent, ORG_ID, incident.getId(), 60));
    }

    // --- Regression coverage for detail-page fixes ---

    @Test
    void assignResponseIncludesAssigneeAndAssignmentTeam() {
        AppUser admin = userWithRole("ADMIN");
        AppUser agent = userWithRole("AGENT");
        Incident incident = incident(null, priority("Medium", 3));
        Team l1 = tierTeam(TIER_L1, "L1 Support");

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(appUserRepository.findById(agent.getId())).thenReturn(Optional.of(agent));
        when(teamMemberRepository.findByUserId(agent.getId()))
                .thenReturn(List.of(membership(TIER_L1, agent)));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, TIER_L1)).thenReturn(Optional.of(l1));
        when(teamRepository.findById(TIER_L1)).thenReturn(Optional.of(l1));

        IncidentResponse response = incidentService.assign(admin, ORG_ID, incident.getId(), agent.getId());

        assertEquals(agent.getId(), response.assigneeId());
        assertEquals(agent.getDisplayName(), response.assignee());
        assertEquals(TIER_L1, response.assignmentTeamId());
        assertEquals("L1 Support", response.assignmentTeamName());
    }

    @Test
    void statusTransitionAccessFollowsAssigneeAndAdminsRetainAccess() {
        AppUser admin = userWithRole("ADMIN");
        AppUser agentA = userWithRole("AGENT");
        AppUser agentB = userWithRole("AGENT");
        Incident assignedToA = incident(agentA, priority("Medium", 3));
        assignedToA.setStatus(Incident.Status.IN_PROGRESS);
        Incident unrelated = incident(agentB, priority("Medium", 3));
        unrelated.setStatus(Incident.Status.IN_PROGRESS);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, assignedToA.getId())).thenReturn(Optional.of(assignedToA));
        when(incidentRepository.findByOrgIdAndId(ORG_ID, unrelated.getId())).thenReturn(Optional.of(unrelated));

        // Assigned agent can transition.
        IncidentResponse responseA = incidentService.updateStatus(agentA, ORG_ID, assignedToA.getId(), Incident.Status.RESOLVED, null);
        assertEquals(Incident.Status.RESOLVED.name(), responseA.status());

        assignedToA.setStatus(Incident.Status.IN_PROGRESS);

        // Other agent cannot transition a ticket not assigned to them.
        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agentB, ORG_ID, assignedToA.getId(), Incident.Status.RESOLVED, null));

        // The same agent cannot transition an unrelated ticket assigned to someone else.
        assertThrows(IllegalStateException.class,
                () -> incidentService.updateStatus(agentA, ORG_ID, unrelated.getId(), Incident.Status.RESOLVED, null));

        // Admin can transition regardless of assignment.
        IncidentResponse responseAdmin = incidentService.updateStatus(admin, ORG_ID, assignedToA.getId(), Incident.Status.RESOLVED, null);
        assertEquals(Incident.Status.RESOLVED.name(), responseAdmin.status());
    }

    @Test
    void addLinkUsesToIncidentIdAndReturnsTargetDetails() {
        AppUser admin = userWithRole("ADMIN");
        Incident from = incident(null, priority("Medium", 3));
        Incident to = incident(null, priority("Medium", 3));
        to.setTitle("Linked incident title");
        to.setStatus(Incident.Status.IN_PROGRESS);

        when(incidentRepository.findByOrgIdAndId(ORG_ID, from.getId())).thenReturn(Optional.of(from));
        when(incidentRepository.findByOrgIdAndId(ORG_ID, to.getId())).thenReturn(Optional.of(to));
        when(linkRepository.save(any(IncidentLink.class))).thenAnswer(inv -> {
            IncidentLink link = inv.getArgument(0);
            link.setId(UUID.randomUUID());
            return link;
        });

        IncidentLinkResponse response = incidentService.addLink(ORG_ID, from.getId(),
                new LinkCreateRequest(to.getId(), "RELATED"), admin.getId());

        assertNotNull(response.id());
        assertEquals(to.getId(), response.toIncidentId());
        assertEquals(to.getNumber(), response.toIncidentNumber());
        assertEquals(to.getTitle(), response.toIncidentTitle());
        assertEquals(to.getStatus().name(), response.toIncidentStatus());
        assertEquals("RELATED", response.linkType());
    }

    @Test
    void listActivityResolvesActorNameAndRole() {
        AppUser actor = userWithRole("AGENT");
        Incident incident = incident(null, priority("Medium", 3));
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setOrgId(ORG_ID);
        log.setActorUserId(actor.getId());
        log.setAction("STATUS");
        log.setEntityType("INCIDENT");
        log.setEntityId(incident.getId());
        log.setBeforeState("{\"status\":\"NEW\"}");
        log.setAfterState("{\"status\":\"IN_PROGRESS\"}");
        log.setCreatedAt(OffsetDateTime.now());

        when(incidentRepository.findByOrgIdAndId(ORG_ID, incident.getId())).thenReturn(Optional.of(incident));
        when(auditLogRepository.findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(ORG_ID, "INCIDENT", incident.getId()))
                .thenReturn(List.of(log));
        when(appUserRepository.findById(actor.getId())).thenReturn(Optional.of(actor));

        List<com.alignedcardio.itsm.api.auth.AuditLogResponse> activity = incidentService.listActivity(ORG_ID, incident.getId());

        assertEquals(1, activity.size());
        com.alignedcardio.itsm.api.auth.AuditLogResponse entry = activity.get(0);
        assertEquals(actor.getDisplayName(), entry.actorName());
        assertEquals("AGENT", entry.actorRole());
    }

    private AppUser userWithRole(String roleName) {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(ORG_ID);
        user.setDisplayName(roleName + " User");
        user.setEmail(roleName.toLowerCase() + "@example.com");

        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        user.setUserRoles(Set.of(userRole));
        return user;
    }

    private Incident incident(AppUser assignee, Priority priority) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setOrgId(ORG_ID);
        incident.setNumber(1L);
        incident.setTitle("Test incident");
        incident.setRequester(userWithRole("END_USER"));
        incident.setAssignee(assignee);
        incident.setPriority(priority);
        incident.setStatus(Incident.Status.NEW);
        return incident;
    }

    private Priority priority(String name, int displayOrder) {
        Priority priority = new Priority();
        priority.setId(UUID.randomUUID());
        priority.setOrgId(ORG_ID);
        priority.setName(name);
        priority.setDisplayOrder(displayOrder);
        priority.setStatus(Priority.Status.ACTIVE);
        return priority;
    }
}
