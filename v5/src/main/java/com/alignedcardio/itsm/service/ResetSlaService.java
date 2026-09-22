package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ServiceRequest;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wipes all SLA tracking state (sla_instance rows) for an org and immediately
 * recreates fresh instances for every currently-open ticket, so reporting starts
 * clean without any ticket silently losing SLA tracking. Configuration
 * (sla_policy, sla_escalation_tier), audit_log, saved_report snapshots, and the
 * tickets themselves are untouched. Dry-run reports exact counts first; the
 * commit path runs in a single transaction.
 */
@Service
public class ResetSlaService {

    private static final Logger log = LoggerFactory.getLogger(ResetSlaService.class);

    private final EntityManager em;
    private final SlaEngine slaEngine;

    public ResetSlaService(EntityManager em, SlaEngine slaEngine) {
        this.em = em;
        this.slaEngine = slaEngine;
    }

    public record ResetResult(boolean dryRun,
                              Map<String, Long> wipedByType,
                              int totalWiped,
                              Map<String, Long> recreatedByType,
                              int totalRecreated) {
    }

    @Transactional
    public ResetResult reset(UUID orgId, boolean dryRun) {
        Map<String, Long> wipedByType = new LinkedHashMap<>();
        wipedByType.put("incident", count("sla_instance", "incident_id IS NOT NULL", orgId));
        wipedByType.put("service_request", count("sla_instance", "service_request_id IS NOT NULL", orgId));
        wipedByType.put("problem", count("sla_instance", "problem_id IS NOT NULL", orgId));
        wipedByType.put("change", count("sla_instance", "change_request_id IS NOT NULL", orgId));
        wipedByType.put("orphaned", count("sla_instance",
                "incident_id IS NULL AND service_request_id IS NULL AND problem_id IS NULL AND change_request_id IS NULL",
                orgId));
        int totalWiped = wipedByType.values().stream().mapToInt(Long::intValue).sum();

        Map<String, Long> recreatedByType = new LinkedHashMap<>();
        recreatedByType.put("incident", 0L);
        recreatedByType.put("service_request", 0L);
        recreatedByType.put("problem", 0L);
        recreatedByType.put("change", 0L);

        List<Incident> openIncidents = em.createQuery(
                        "SELECT i FROM Incident i WHERE i.orgId = :org AND i.deletedAt IS NULL " +
                                "AND i.status NOT IN :terminal", Incident.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(Incident.Status.RESOLVED, Incident.Status.CLOSED))
                .getResultList();
        List<ServiceRequest> openRequests = em.createQuery(
                        "SELECT s FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                                "AND s.status NOT IN :terminal", ServiceRequest.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(ServiceRequest.Status.FULFILLED,
                        ServiceRequest.Status.REJECTED, ServiceRequest.Status.CANCELLED))
                .getResultList();
        List<Problem> openProblems = em.createQuery(
                        "SELECT p FROM Problem p WHERE p.orgId = :org AND p.deletedAt IS NULL " +
                                "AND p.status NOT IN :terminal", Problem.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(Problem.Status.RESOLVED, Problem.Status.CLOSED))
                .getResultList();
        List<ChangeRequest> openChanges = em.createQuery(
                        "SELECT c FROM ChangeRequest c WHERE c.orgId = :org AND c.deletedAt IS NULL " +
                                "AND c.status NOT IN :terminal", ChangeRequest.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(ChangeRequest.Status.COMPLETED, ChangeRequest.Status.FAILED,
                        ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED,
                        ChangeRequest.Status.CLOSED, ChangeRequest.Status.REJECTED))
                .getResultList();

        if (dryRun) {
            // Report how many open tickets would be candidates for a fresh
            // instance (a matching policy with a calendar decides at recreate).
            recreatedByType.put("incident", (long) openIncidents.size());
            recreatedByType.put("service_request", (long) openRequests.size());
            recreatedByType.put("problem", (long) openProblems.size());
            recreatedByType.put("change", (long) openChanges.size());
            int candidates = openIncidents.size() + openRequests.size() + openProblems.size() + openChanges.size();
            log.info("SLA reset dry-run for org {}: {} instances would be wiped, {} open tickets are recreate candidates",
                    orgId, totalWiped, candidates);
            return new ResetResult(true, wipedByType, totalWiped, recreatedByType, candidates);
        }

        em.createNativeQuery("DELETE FROM sla_instance WHERE org_id = :org")
                .setParameter("org", orgId)
                .executeUpdate();
        em.flush();

        // Recreated clocks anchor at reset time — the ticket's original
        // createdAt would backdate every due date into the past and make the
        // just-reset instances instantly breached.
        java.time.OffsetDateTime resetAt = java.time.OffsetDateTime.now();
        int recreated = 0;
        for (Incident i : openIncidents) {
            if (slaEngine.onIncidentCreated(i, resetAt)) {
                recreated++;
                recreatedByType.merge("incident", 1L, Long::sum);
            }
        }
        for (ServiceRequest s : openRequests) {
            if (slaEngine.onServiceRequestCreated(s, resetAt)) {
                recreated++;
                recreatedByType.merge("service_request", 1L, Long::sum);
            }
        }
        for (Problem p : openProblems) {
            if (slaEngine.onProblemCreated(p, resetAt)) {
                recreated++;
                recreatedByType.merge("problem", 1L, Long::sum);
            }
        }
        for (ChangeRequest c : openChanges) {
            if (slaEngine.onChangeCreated(c, resetAt)) {
                recreated++;
                recreatedByType.merge("change", 1L, Long::sum);
            }
        }

        log.info("SLA reset for org {}: wiped {} instances, recreated {}", orgId, totalWiped, recreated);
        return new ResetResult(false, wipedByType, totalWiped, recreatedByType, recreated);
    }

    private long count(String table, String where, UUID orgId) {
        return ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM " + table + " WHERE org_id = :org AND " + where)
                .setParameter("org", orgId)
                .getSingleResult()).longValue();
    }
}
