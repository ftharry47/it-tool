package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.SlaEscalationTier;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.SlaEscalationTierRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaBreachMonitorJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock private SlaInstanceRepository slaInstanceRepository;
    @Mock private SlaEngine slaEngine;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private SlaEscalationTierRepository escalationTierRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private AuditLogRepository auditLogRepository;

    private SlaBreachMonitorJob job;

    @BeforeEach
    void setup() {
        job = new SlaBreachMonitorJob(slaInstanceRepository, slaEngine, eventPublisher,
                notificationService,
                new com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder("http://localhost:8080"),
                escalationTierRepository, appUserRepository,
                teamRepository, incidentRepository,
                auditLogRepository, new ObjectMapper());
    }

    @Test
    void escalatesExactlyOneTierPerSweepAndDoesNotRefire() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.IN_PROGRESS);
        SlaInstance instance = instance(policy, incident);
        instance.setResponseDueAt(OffsetDateTime.now().minusMinutes(10)); // breached

        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_RESPONSE_BREACH);
        SlaEscalationTier t2 = tier(policy, 2, SlaEscalationTier.TriggerType.ON_RESPONSE_BREACH);
        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1, t2));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        // First sweep: fires tier 1 only, even though tier 2's trigger is also met
        job.execute(null);
        assertEquals(1, instance.getEscalationLevel());

        // Second sweep: fires tier 2 (next tier), not tier 1 again
        job.execute(null);
        assertEquals(2, instance.getEscalationLevel());

        // Third sweep: no tiers left, level unchanged
        job.execute(null);
        assertEquals(2, instance.getEscalationLevel());
    }

    @Test
    void resolvedIncidentIsExcludedFromEscalation() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.RESOLVED);
        SlaInstance instance = instance(policy, incident);
        instance.setResponseDueAt(OffsetDateTime.now().minusMinutes(10));

        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        assertEquals(0, instance.getEscalationLevel());
        verifyNoInteractions(escalationTierRepository);
    }

    @Test
    void closedIncidentIsExcludedFromEscalation() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.CLOSED);
        SlaInstance instance = instance(policy, incident);
        instance.setResolutionDueAt(OffsetDateTime.now().minusMinutes(10));

        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        assertEquals(0, instance.getEscalationLevel());
        verifyNoInteractions(escalationTierRepository);
    }

    @Test
    void resolutionBreachTriggerFiresEscalation() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.IN_PROGRESS);
        SlaInstance instance = instance(policy, incident);
        instance.setResolutionDueAt(OffsetDateTime.now().minusMinutes(5)); // resolution breached

        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_RESOLUTION_BREACH);
        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        assertEquals(1, instance.getEscalationLevel());
    }

    @Test
    void stuckStatusTriggerFiresEscalation() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.NEW);
        incident.setUpdatedAt(OffsetDateTime.now().minusMinutes(45)); // stuck 45 min
        SlaInstance instance = instance(policy, incident);

        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_STUCK_STATUS);
        t1.setStuckStatus("NEW");
        t1.setStuckMinutes(30);
        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        assertEquals(1, instance.getEscalationLevel());
    }

    @Test
    void stuckStatusTriggerDoesNotFireBeforeThreshold() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.NEW);
        incident.setUpdatedAt(OffsetDateTime.now().minusMinutes(10)); // only 10 min stuck
        SlaInstance instance = instance(policy, incident);

        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_STUCK_STATUS);
        t1.setStuckStatus("NEW");
        t1.setStuckMinutes(30);
        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        assertEquals(0, instance.getEscalationLevel());
    }

    @Test
    void higherTierFiresWhenLowerTierTriggerNotMet() {
        // Regression: a fast-acknowledged ticket (never stuck in NEW) that later
        // breaches resolution must still escalate — tiers are independent
        // candidates, not a strict sequential chain.
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.IN_PROGRESS);
        incident.setUpdatedAt(OffsetDateTime.now().minusMinutes(5)); // not stuck
        SlaInstance instance = instance(policy, incident);
        instance.setResolutionDueAt(OffsetDateTime.now().minusMinutes(10)); // breached

        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_STUCK_STATUS);
        t1.setStuckStatus("NEW");
        t1.setStuckMinutes(30);
        SlaEscalationTier t2 = tier(policy, 2, SlaEscalationTier.TriggerType.ON_RESOLUTION_BREACH);
        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1, t2));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));

        job.execute(null);

        // Tier 1's trigger is not met, but tier 2's is — it fires directly.
        assertEquals(2, instance.getEscalationLevel());
    }

    @Test
    void escalationNotifiesRoleAndReassignsTeam() {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.IN_PROGRESS);
        SlaInstance instance = instance(policy, incident);
        instance.setResponseDueAt(OffsetDateTime.now().minusMinutes(10));

        Team team = new Team();
        team.setId(UUID.randomUUID());
        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_RESPONSE_BREACH);
        t1.setNotifyRole("TEAM_LEAD");
        t1.setReassignToTeamId(team.getId());

        AppUser lead = new AppUser();
        lead.setId(UUID.randomUUID());

        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));
        when(appUserRepository.findByOrgIdAndRoleNames(ORG_ID, List.of("TEAM_LEAD")))
                .thenReturn(List.of(lead));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, team.getId()))
                .thenReturn(Optional.of(team));

        job.execute(null);

        assertEquals(1, instance.getEscalationLevel());
        verify(notificationService).send(any(NotificationRequest.class));
        verify(incidentRepository).save(incident);
        assertSame(team, incident.getAssignmentTeam());
    }

    @Test
    void autoEscalationWritesAuditWithNullActorAndPreEscalationAssignee() throws Exception {
        SlaPolicy policy = policy();
        Incident incident = incident(Incident.Status.IN_PROGRESS);
        AppUser oldAssignee = new AppUser();
        oldAssignee.setId(UUID.randomUUID());
        incident.setAssignee(oldAssignee);
        Team oldTeam = new Team();
        oldTeam.setId(UUID.randomUUID());
        oldTeam.setName("L1 Support");
        incident.setAssignmentTeam(oldTeam);

        SlaInstance instance = instance(policy, incident);
        instance.setResponseDueAt(OffsetDateTime.now().minusMinutes(10));

        Team newTeam = new Team();
        newTeam.setId(UUID.randomUUID());
        newTeam.setName("L2 Support");
        SlaEscalationTier t1 = tier(policy, 1, SlaEscalationTier.TriggerType.ON_RESPONSE_BREACH);
        t1.setReassignToTeamId(newTeam.getId());

        when(escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId()))
                .thenReturn(List.of(t1));
        when(slaInstanceRepository.findByBreachStatusIn(any())).thenReturn(List.of(instance));
        when(teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(ORG_ID, newTeam.getId()))
                .thenReturn(Optional.of(newTeam));

        job.execute(null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog log = captor.getValue();
        assertEquals("AUTO_ESCALATE_TIER", log.getAction());
        assertNull(log.getActorUserId()); // system actor — job-triggered
        assertEquals("INCIDENT", log.getEntityType());
        assertEquals(incident.getId(), log.getEntityId());
        // beforeState attributes the escalation-away to the old assignee.
        assertTrue(log.getBeforeState().contains(oldAssignee.getId().toString()));
        assertTrue(log.getBeforeState().contains("L1 Support"));
        assertTrue(log.getAfterState().contains("L2 Support"));
    }

    private SlaPolicy policy() {
        SlaPolicy policy = new SlaPolicy();
        policy.setId(UUID.randomUUID());
        policy.setOrgId(ORG_ID);
        return policy;
    }

    private Incident incident(Incident.Status status) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setOrgId(ORG_ID);
        incident.setStatus(status);
        incident.setNumber(1000L);
        incident.setUpdatedAt(OffsetDateTime.now());
        return incident;
    }

    private SlaInstance instance(SlaPolicy policy, Incident incident) {
        SlaInstance instance = new SlaInstance();
        instance.setId(UUID.randomUUID());
        instance.setOrgId(ORG_ID);
        instance.setPolicy(policy);
        instance.setIncident(incident);
        return instance;
    }

    private SlaEscalationTier tier(SlaPolicy policy, int level, SlaEscalationTier.TriggerType trigger) {
        SlaEscalationTier tier = new SlaEscalationTier();
        tier.setId(UUID.randomUUID());
        tier.setOrgId(ORG_ID);
        tier.setPolicy(policy);
        tier.setLevel(level);
        tier.setTriggerType(trigger);
        return tier;
    }
}
