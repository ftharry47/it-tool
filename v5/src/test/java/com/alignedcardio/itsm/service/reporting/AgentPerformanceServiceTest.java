package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPerformanceServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock private IncidentRepository incidentRepository;
    @Mock private FulfillmentTaskRepository fulfillmentTaskRepository;
    @Mock private SlaInstanceRepository slaInstanceRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private AppUserRepository appUserRepository;
    @Mock private com.alignedcardio.itsm.repository.ProblemRepository problemRepository;
    @Mock private com.alignedcardio.itsm.repository.ChangeRequestRepository changeRequestRepository;

    private AgentPerformanceService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        lenient().when(problemRepository.findByOrgIdAndAssigneeIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        lenient().when(changeRequestRepository.findByOrgIdAndAssigneeIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of());
        service = new AgentPerformanceService(incidentRepository, fulfillmentTaskRepository,
                slaInstanceRepository, auditLogRepository, appUserRepository,
                problemRepository, changeRequestRepository, objectMapper);
    }

    private AppUser agent() {
        AppUser u = new AppUser();
        u.setId(UUID.randomUUID());
        u.setOrgId(ORG_ID);
        u.setDisplayName("Agent One");
        return u;
    }

    private Incident resolvedIncident(AppUser assignee, OffsetDateTime created, OffsetDateTime resolved) {
        Incident i = new Incident();
        i.setId(UUID.randomUUID());
        i.setOrgId(ORG_ID);
        i.setAssignee(assignee);
        i.setStatus(Incident.Status.RESOLVED);
        i.setCreatedAt(created);
        i.setResolvedAt(resolved);
        return i;
    }

    private AuditLog audit(String action, UUID actorId, UUID entityId, String beforeState, OffsetDateTime at) {
        AuditLog log = new AuditLog();
        log.setOrgId(ORG_ID);
        log.setAction(action);
        log.setActorUserId(actorId);
        log.setEntityType("INCIDENT");
        log.setEntityId(entityId);
        log.setBeforeState(beforeState);
        log.setCreatedAt(at);
        return log;
    }

    @Test
    void scorePenalizesOnlyAutomaticEscalationsNotManualSelfEscalations() {
        AppUser agent = agent();
        YearMonth month = YearMonth.now().minusMonths(1);
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime within = from.plusDays(5);

        // One resolved incident, no SLA → base = resolutionRate = 100.
        Incident inc = resolvedIncident(agent, within, within.plusHours(2));
        when(incidentRepository.findByOrgIdAndAssigneeIdAndCreatedAtBetween(eq(ORG_ID), eq(agent.getId()), any(), any()))
                .thenReturn(List.of(inc));
        when(fulfillmentTaskRepository.findByAssignee_IdAndAssignedAtBetween(eq(agent.getId()), any(), any()))
                .thenReturn(List.of());
        when(slaInstanceRepository.findByIncidentIdIn(any())).thenReturn(List.of());

        // Audit trail: 1 AUTO escalation away from this agent (penalized -3)
        // + 2 MANUAL self-escalations BY this agent (NOT penalized).
        String autoBefore = "{\"assigneeId\":\"" + agent.getId() + "\"}";
        List<AuditLog> logs = List.of(
                audit("AUTO_ESCALATE_TIER", null, inc.getId(), autoBefore, within.plusHours(1)),
                audit("ESCALATE_TIER", agent.getId(), inc.getId(), null, within.plusHours(2)),
                audit("ESCALATE_TIER", agent.getId(), UUID.randomUUID(), null, within.plusHours(3)));
        when(auditLogRepository.findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                eq(ORG_ID), eq("INCIDENT"), anyList(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    List<String> actions = inv.getArgument(2);
                    List<AuditLog> filtered = logs.stream()
                            .filter(l -> actions.contains(l.getAction())).toList();
                    return new PageImpl<>(filtered);
                });

        AgentPerformanceService.AgentPerformanceReport r = service.forMonth(agent, month);

        assertEquals(1, r.autoEscalationsAway());
        assertEquals(2, r.manualSelfEscalations());
        // base 100 (resolutionRate) − 3 (one auto escalation) = 97 → A
        assertEquals(97, r.score());
        assertEquals("A", r.grade());
    }

    @Test
    void scoreUsesSlaComplianceAsBaseWhenSlaTicketsExist() {
        AppUser agent = agent();
        YearMonth month = YearMonth.now().minusMonths(1);
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime within = from.plusDays(5);

        Incident inc = resolvedIncident(agent, within, within.plusHours(2));
        when(incidentRepository.findByOrgIdAndAssigneeIdAndCreatedAtBetween(eq(ORG_ID), eq(agent.getId()), any(), any()))
                .thenReturn(List.of(inc));
        when(fulfillmentTaskRepository.findByAssignee_IdAndAssignedAtBetween(eq(agent.getId()), any(), any()))
                .thenReturn(List.of());

        // SLA met → compliance 100.
        SlaInstance sla = new SlaInstance();
        sla.setResolutionDueAt(within.plusHours(4));
        sla.setResolutionMetAt(within.plusHours(2));
        sla.setBreachStatus(SlaInstance.BreachStatus.ON_TRACK);
        when(slaInstanceRepository.findByIncidentIdIn(any())).thenReturn(List.of(sla));

        when(auditLogRepository.findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                eq(ORG_ID), eq("INCIDENT"), anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        AgentPerformanceService.AgentPerformanceReport r = service.forMonth(agent, month);

        assertEquals(100.0, r.slaCompliancePct());
        assertEquals(0, r.breachCount());
        assertEquals(100, r.score());
        assertEquals("A", r.grade());
    }

    @Test
    void breachAndReopenPenaltiesApply() {
        AppUser agent = agent();
        YearMonth month = YearMonth.now().minusMonths(1);
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime within = from.plusDays(5);

        Incident inc = resolvedIncident(agent, within, within.plusHours(2));
        when(incidentRepository.findByOrgIdAndAssigneeIdAndCreatedAtBetween(eq(ORG_ID), eq(agent.getId()), any(), any()))
                .thenReturn(List.of(inc));
        when(fulfillmentTaskRepository.findByAssignee_IdAndAssignedAtBetween(eq(agent.getId()), any(), any()))
                .thenReturn(List.of());

        // SLA breached → compliance 0, breachCount 1.
        SlaInstance sla = new SlaInstance();
        sla.setResolutionDueAt(within.plusHours(1));
        sla.setResolutionMetAt(within.plusHours(2)); // met AFTER due → breached
        sla.setBreachStatus(SlaInstance.BreachStatus.BREACHED);
        when(slaInstanceRepository.findByIncidentIdIn(any())).thenReturn(List.of(sla));

        // One REOPEN on the resolved incident.
        AuditLog reopen = audit("REOPEN", UUID.randomUUID(), inc.getId(), null, within.plusHours(3));
        when(auditLogRepository.findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                eq(ORG_ID), eq("INCIDENT"), anyList(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    List<String> actions = inv.getArgument(2);
                    List<AuditLog> filtered = List.of(reopen).stream()
                            .filter(l -> actions.contains(l.getAction())).toList();
                    return new PageImpl<>(filtered);
                });

        AgentPerformanceService.AgentPerformanceReport r = service.forMonth(agent, month);

        assertEquals(0.0, r.slaCompliancePct());
        assertEquals(1, r.breachCount());
        assertEquals(1, r.reopenedCount());
        // base 0 − 5 (reopen) − 2 (breach) = -7 → clamped to 0 → F
        assertEquals(0, r.score());
        assertEquals("F", r.grade());
    }

    /**
     * Regression: escalatedAwayPct must be bounded to 0-100%. The bug: the
     * numerator counted escalation EVENTS (one audit row each) while the
     * denominator counted distinct handled tickets — one ticket escalated
     * twice (auto + manual self) yielded 200%. The numerator is now distinct
     * handled ticket IDs, a strict subset of the denominator.
     */
    @Test
    void escalatedAwayPctCountsEachTicketOnceAndStaysBounded() {
        AppUser agent = agent();
        YearMonth month = YearMonth.now().minusMonths(1);
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime within = from.plusDays(5);

        Incident inc = resolvedIncident(agent, within, within.plusHours(2));
        when(incidentRepository.findByOrgIdAndAssigneeIdAndCreatedAtBetween(eq(ORG_ID), eq(agent.getId()), any(), any()))
                .thenReturn(List.of(inc));
        when(fulfillmentTaskRepository.findByAssignee_IdAndAssignedAtBetween(eq(agent.getId()), any(), any()))
                .thenReturn(List.of());
        when(slaInstanceRepository.findByIncidentIdIn(any())).thenReturn(List.of());

        // The SAME ticket escalated away twice: one auto + one manual self.
        String autoBefore = "{\"assigneeId\":\"" + agent.getId() + "\"}";
        List<AuditLog> logs = List.of(
                audit("AUTO_ESCALATE_TIER", null, inc.getId(), autoBefore, within.plusHours(1)),
                audit("ESCALATE_TIER", agent.getId(), inc.getId(), null, within.plusHours(2)),
                // plus a manual escalation on a ticket NOT in this period's handled set
                audit("ESCALATE_TIER", agent.getId(), UUID.randomUUID(), null, within.plusHours(3)));
        when(auditLogRepository.findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                eq(ORG_ID), eq("INCIDENT"), anyList(), any(Pageable.class)))
                .thenAnswer(inv -> {
                    List<String> actions = inv.getArgument(2);
                    return new PageImpl<>(logs.stream()
                            .filter(l -> actions.contains(l.getAction())).toList());
                });

        AgentPerformanceService.AgentPerformanceReport r = service.forMonth(agent, month);

        // Event counts still reflect reality: 1 auto + 2 manual events.
        assertEquals(1, r.autoEscalationsAway());
        assertEquals(2, r.manualSelfEscalations());
        // But the percentage is distinct handled tickets: 1/1 = 100%, not 300%.
        assertEquals(100.0, r.escalatedAwayPct());
        assertTrue(r.escalatedAwayPct() <= 100.0);
    }

    @Test
    void noReportWhenAgentHandledNothing() {
        AppUser agent = agent();
        YearMonth month = YearMonth.now().minusMonths(1);
        when(appUserRepository.findByOrgId(ORG_ID)).thenReturn(List.of(agent));
        when(incidentRepository.findByOrgIdAndAssigneeIdAndCreatedAtBetween(eq(ORG_ID), eq(agent.getId()), any(), any()))
                .thenReturn(List.of());
        when(fulfillmentTaskRepository.findByAssignee_IdAndAssignedAtBetween(eq(agent.getId()), any(), any()))
                .thenReturn(List.of());
        when(auditLogRepository.findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                eq(ORG_ID), eq("INCIDENT"), anyList(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        List<AgentPerformanceService.AgentPerformanceReport> reports = service.forAllAgents(ORG_ID, month);
        assertTrue(reports.isEmpty());
    }
}
