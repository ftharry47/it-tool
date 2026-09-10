package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Computes per-agent performance metrics over a period, covering both
 * Incidents and Fulfillment tasks, and derives a 0-100 score + letter grade.
 *
 * Scoring (approved formula):
 *   base  = slaCompliancePct  (or resolutionRate*100 when no SLA-covered work)
 *   - reopenedCount        * 5
 *   - autoEscalationsAway  * 3   (AUTO_ESCALATE_TIER only — manual self-escalation is not penalized)
 *   - breachCount          * 2
 *   + volumeBonus (+5 if handled >= 20, +3 if >= 10)
 *   clamped to [0, 100]; grade A>=90 B>=80 C>=70 D>=60 F<60
 */
@Service
public class AgentPerformanceService {

    private final IncidentRepository incidentRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final SlaInstanceRepository slaInstanceRepository;
    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public AgentPerformanceService(IncidentRepository incidentRepository,
                                   FulfillmentTaskRepository fulfillmentTaskRepository,
                                   SlaInstanceRepository slaInstanceRepository,
                                   AuditLogRepository auditLogRepository,
                                   AppUserRepository appUserRepository,
                                   ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.slaInstanceRepository = slaInstanceRepository;
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    public record AgentPerformanceReport(
            UUID agentId,
            String agentName,
            String period,               // e.g. "2026-08"
            int ticketsHandled,
            int ticketsResolved,
            double resolutionRate,       // 0-100
            double slaCompliancePct,     // 0-100, -1 when no SLA-covered work
            int slaEvaluated,
            int breachCount,
            int reopenedCount,
            long avgResolutionMinutes,
            int autoEscalationsAway,     // penalized
            int manualSelfEscalations,   // informational, not penalized
            int score,                   // 0-100
            String grade                 // A-F
    ) {}

    /** Live metrics for the current month-to-date. */
    @Transactional(readOnly = true)
    public AgentPerformanceReport currentMonth(AppUser agent) {
        YearMonth ym = YearMonth.now();
        return compute(agent, ym.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset()),
                OffsetDateTime.now(), ym.toString());
    }

    /** Frozen metrics for a completed month. */
    @Transactional(readOnly = true)
    public AgentPerformanceReport forMonth(AppUser agent, YearMonth month) {
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime to = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        return compute(agent, from, to, month.toString());
    }

    /**
     * Rolling (all-time) SLA compliance % for an agent across their incidents.
     * Returns -1 when the agent has no SLA-covered incidents.
     */
    @Transactional(readOnly = true)
    public double rollingSlaCompliance(AppUser agent) {
        List<Incident> incidents = incidentRepository
                .findByOrgIdAndAssigneeIdAndStatusInOrderByCreatedAtDesc(
                        agent.getOrgId(), agent.getId(),
                        List.of(Incident.Status.values()),
                        PageRequest.of(0, 1000));
        List<UUID> ids = incidents.stream().map(Incident::getId).toList();
        if (ids.isEmpty()) return -1;
        int evaluated = 0;
        int met = 0;
        for (SlaInstance sla : slaInstanceRepository.findByIncidentIdIn(ids)) {
            if (sla.getResolutionDueAt() != null) {
                evaluated++;
                if (sla.getResolutionMetAt() != null && !sla.getResolutionMetAt().isAfter(sla.getResolutionDueAt())) met++;
            }
        }
        return evaluated > 0 ? Math.round(met * 1000.0 / evaluated) / 10.0 : -1;
    }

    /** All agents (any staff role) who handled at least one ticket in the period. */
    @Transactional(readOnly = true)
    public List<AgentPerformanceReport> forAllAgents(UUID orgId, YearMonth month) {
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime to = month.plusMonths(1).atDay(1).atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        List<AgentPerformanceReport> reports = new ArrayList<>();
        for (AppUser user : appUserRepository.findByOrgId(orgId)) {
            AgentPerformanceReport r = compute(user, from, to, month.toString());
            if (r.ticketsHandled() > 0) {
                reports.add(r);
            }
        }
        return reports;
    }

    private AgentPerformanceReport compute(AppUser agent, OffsetDateTime from, OffsetDateTime to, String period) {
        UUID agentId = agent.getId();
        UUID orgId = agent.getOrgId();

        // --- Incidents assigned to the agent created in the period ---
        List<Incident> incidents = incidentRepository
                .findByOrgIdAndAssigneeIdAndCreatedAtBetween(orgId, agentId, from, to);

        // --- Fulfillment tasks assigned in the period ---
        List<FulfillmentTask> tasks = fulfillmentTaskRepository
                .findByAssignee_IdAndAssignedAtBetween(agentId, from, to);

        int handled = incidents.size() + tasks.size();

        int resolvedIncidents = 0;
        long totalResolutionMinutes = 0;
        int resolutionCount = 0;
        List<UUID> resolvedIncidentIds = new ArrayList<>();
        for (Incident inc : incidents) {
            boolean resolved = (inc.getStatus() == Incident.Status.RESOLVED || inc.getStatus() == Incident.Status.CLOSED)
                    && inc.getResolvedAt() != null
                    && !inc.getResolvedAt().isBefore(from) && inc.getResolvedAt().isBefore(to);
            if (resolved) {
                resolvedIncidents++;
                resolvedIncidentIds.add(inc.getId());
                totalResolutionMinutes += Duration.between(inc.getCreatedAt(), inc.getResolvedAt()).toMinutes();
                resolutionCount++;
            }
        }

        int completedTasks = 0;
        for (FulfillmentTask t : tasks) {
            if (t.getStatus() == FulfillmentTask.Status.COMPLETED
                    && t.getCompletedAt() != null
                    && !t.getCompletedAt().isBefore(from) && t.getCompletedAt().isBefore(to)) {
                completedTasks++;
                if (t.getAssignedAt() != null) {
                    totalResolutionMinutes += Duration.between(t.getAssignedAt(), t.getCompletedAt()).toMinutes();
                    resolutionCount++;
                }
            }
        }

        int resolved = resolvedIncidents + completedTasks;
        double resolutionRate = handled > 0 ? (resolved * 100.0 / handled) : 0;

        // --- SLA compliance across the agent's incidents in the period ---
        List<UUID> incidentIds = incidents.stream().map(Incident::getId).toList();
        List<SlaInstance> slas = incidentIds.isEmpty()
                ? List.of()
                : slaInstanceRepository.findByIncidentIdIn(incidentIds);
        int slaEvaluated = 0;
        int slaMet = 0;
        int breachCount = 0;
        for (SlaInstance sla : slas) {
            if (sla.getResolutionDueAt() != null) {
                slaEvaluated++;
                boolean met = sla.getResolutionMetAt() != null && !sla.getResolutionMetAt().isAfter(sla.getResolutionDueAt());
                if (met) slaMet++;
                if (sla.getBreachStatus() == SlaInstance.BreachStatus.BREACHED) breachCount++;
            }
        }
        double slaCompliancePct = slaEvaluated > 0 ? (slaMet * 100.0 / slaEvaluated) : -1;

        // --- Reopens on incidents the agent resolved ---
        int reopenedCount = 0;
        if (!resolvedIncidentIds.isEmpty()) {
            for (AuditLog log : auditLogRepository
                    .findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                            orgId, "INCIDENT", List.of("REOPEN"), PageRequest.of(0, 500)).getContent()) {
                if (resolvedIncidentIds.contains(log.getEntityId())
                        && !log.getCreatedAt().isBefore(from) && log.getCreatedAt().isBefore(to)) {
                    reopenedCount++;
                }
            }
        }

        // --- Escalations: AUTO (penalized) vs manual self-escalation (not) ---
        int autoAway = 0;
        int manualSelf = 0;
        for (AuditLog log : auditLogRepository
                .findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
                        orgId, "INCIDENT", List.of("AUTO_ESCALATE_TIER", "ESCALATE_TIER"),
                        PageRequest.of(0, 500)).getContent()) {
            if (log.getCreatedAt().isBefore(from) || !log.getCreatedAt().isBefore(to)) continue;
            if ("AUTO_ESCALATE_TIER".equals(log.getAction())) {
                if (agentId.equals(extractAssigneeId(log.getBeforeState()))) autoAway++;
            } else if ("ESCALATE_TIER".equals(log.getAction())) {
                if (agentId.equals(log.getActorUserId())) manualSelf++;
            }
        }

        long avgResolutionMinutes = resolutionCount > 0 ? totalResolutionMinutes / resolutionCount : 0;

        // --- Score ---
        double base = slaCompliancePct >= 0 ? slaCompliancePct : resolutionRate;
        int volumeBonus = handled >= 20 ? 5 : (handled >= 10 ? 3 : 0);
        double raw = base
                - (reopenedCount * 5.0)
                - (autoAway * 3.0)
                - (breachCount * 2.0)
                + volumeBonus;
        int score = (int) Math.round(Math.max(0, Math.min(100, raw)));
        String grade = score >= 90 ? "A" : score >= 80 ? "B" : score >= 70 ? "C" : score >= 60 ? "D" : "F";

        return new AgentPerformanceReport(
                agentId, agent.getDisplayName(), period,
                handled, resolved, Math.round(resolutionRate * 10) / 10.0,
                slaCompliancePct >= 0 ? Math.round(slaCompliancePct * 10) / 10.0 : -1,
                slaEvaluated, breachCount, reopenedCount, avgResolutionMinutes,
                autoAway, manualSelf, score, grade);
    }

    private UUID extractAssigneeId(String beforeStateJson) {
        if (beforeStateJson == null) return null;
        try {
            JsonNode node = objectMapper.readTree(beforeStateJson).get("assigneeId");
            return node != null && !node.isNull() ? UUID.fromString(node.asText()) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
