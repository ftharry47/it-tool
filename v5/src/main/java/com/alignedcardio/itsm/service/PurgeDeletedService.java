package com.alignedcardio.itsm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Permanently hard-deletes soft-deleted Incidents and Service Requests, including
 * every FK-constrained child row, polymorphic audit/notification/time-entry rows,
 * and on-disk attachment files. Exposed via an explicit admin endpoint rather than
 * a Flyway migration so the destructive action is deliberate and auditable.
 */
@Service
public class PurgeDeletedService {

    private static final Logger log = LoggerFactory.getLogger(PurgeDeletedService.class);

    private final EntityManager em;
    private final String storePath;

    public PurgeDeletedService(EntityManager em,
                               @Value("${attachment.store.path:target/attachments}") String storePath) {
        this.em = em;
        this.storePath = storePath;
    }

    public record PurgeResult(boolean dryRun, int incidentsPurged, int serviceRequestsPurged,
                              int attachmentDirsRemoved, Map<String, Long> counts) {
    }

    @Transactional
    public PurgeResult purge(UUID orgId, boolean dryRun) {
        List<UUID> incidentIds = softDeletedIds("incident", orgId);
        List<UUID> srIds = softDeletedIds("service_request", orgId);

        Map<String, Long> counts = new LinkedHashMap<>();
        // FK children of incident
        counts.put("incident_link", affected("incident_link",
                "from_incident_id IN (:ids) OR to_incident_id IN (:ids)", incidentIds));
        counts.put("problem_incident_link", affected("problem_incident_link", "incident_id IN (:ids)", incidentIds));
        counts.put("incident_watcher", affected("incident_watcher", "incident_id IN (:ids)", incidentIds));
        counts.put("incident_comment", affected("incident_comment", "incident_id IN (:ids)", incidentIds));
        counts.put("incident_attachment", affected("incident_attachment", "incident_id IN (:ids)", incidentIds));
        counts.put("sla_instance(incident)", affected("sla_instance", "incident_id IN (:ids)", incidentIds));
        // polymorphic rows referencing incidents
        counts.put("automation_run_log(INCIDENT)", polyAffected("automation_run_log", "INCIDENT", incidentIds));
        counts.put("audit_log(INCIDENT)", polyAffected("audit_log", "INCIDENT", incidentIds));
        counts.put("notification(INCIDENT)", polyAffected("notification", "INCIDENT", incidentIds));
        counts.put("time_entry(INCIDENT)", polyAffected("time_entry", "INCIDENT", incidentIds));
        // FK children of service_request
        counts.put("fulfillment_task", affected("fulfillment_task", "service_request_id IN (:ids)", srIds));
        counts.put("service_request_comment", affected("service_request_comment", "service_request_id IN (:ids)", srIds));
        counts.put("sla_instance(service_request)", affected("sla_instance", "service_request_id IN (:ids)", srIds));
        counts.put("automation_run_log(SERVICE_REQUEST)", polyAffected("automation_run_log", "SERVICE_REQUEST", srIds));
        counts.put("audit_log(SERVICE_REQUEST)", polyAffected("audit_log", "SERVICE_REQUEST", srIds));
        counts.put("notification(SERVICE_REQUEST)", polyAffected("notification", "SERVICE_REQUEST", srIds));
        counts.put("time_entry(SERVICE_REQUEST)", polyAffected("time_entry", "SERVICE_REQUEST", srIds));
        // parents
        counts.put("incident", (long) incidentIds.size());
        counts.put("service_request", (long) srIds.size());

        int attachmentDirsRemoved = 0;
        if (!dryRun) {
            delete("incident_link", "from_incident_id IN (:ids) OR to_incident_id IN (:ids)", incidentIds);
            delete("problem_incident_link", "incident_id IN (:ids)", incidentIds);
            delete("incident_watcher", "incident_id IN (:ids)", incidentIds);
            delete("incident_comment", "incident_id IN (:ids)", incidentIds);
            delete("incident_attachment", "incident_id IN (:ids)", incidentIds);
            delete("sla_instance", "incident_id IN (:ids)", incidentIds);
            polyDelete("INCIDENT", incidentIds);

            delete("fulfillment_task", "service_request_id IN (:ids)", srIds);
            delete("service_request_comment", "service_request_id IN (:ids)", srIds);
            delete("sla_instance", "service_request_id IN (:ids)", srIds);
            polyDelete("SERVICE_REQUEST", srIds);

            attachmentDirsRemoved = deleteAttachmentDirs(orgId, incidentIds);

            delete("incident", "id IN (:ids)", incidentIds);
            delete("service_request", "id IN (:ids)", srIds);
            log.info("Purged {} incidents, {} service requests for org {} ({} attachment dirs removed)",
                    incidentIds.size(), srIds.size(), orgId, attachmentDirsRemoved);
        }
        return new PurgeResult(dryRun, incidentIds.size(), srIds.size(), attachmentDirsRemoved, counts);
    }

    private List<UUID> softDeletedIds(String table, UUID orgId) {
        Query q = em.createNativeQuery(
                "SELECT id FROM " + table + " WHERE org_id = :org AND deleted_at IS NOT NULL");
        q.setParameter("org", orgId);
        List<UUID> ids = new ArrayList<>();
        for (Object row : q.getResultList()) {
            ids.add((UUID) row);
        }
        return ids;
    }

    private long affected(String table, String where, List<UUID> ids) {
        if (ids.isEmpty()) return 0;
        Query q = em.createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where);
        q.setParameter("ids", ids);
        return ((Number) q.getSingleResult()).longValue();
    }

    private long polyAffected(String table, String entityType, List<UUID> ids) {
        if (ids.isEmpty()) return 0;
        Query q = em.createNativeQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE entity_type = :et AND entity_id IN (:ids)");
        q.setParameter("et", entityType);
        q.setParameter("ids", ids);
        return ((Number) q.getSingleResult()).longValue();
    }

    private void delete(String table, String where, List<UUID> ids) {
        if (ids.isEmpty()) return;
        Query q = em.createNativeQuery("DELETE FROM " + table + " WHERE " + where);
        q.setParameter("ids", ids);
        q.executeUpdate();
    }

    private void polyDelete(String entityType, List<UUID> ids) {
        if (ids.isEmpty()) return;
        for (String table : new String[]{"automation_run_log", "audit_log", "notification", "time_entry"}) {
            Query q = em.createNativeQuery(
                    "DELETE FROM " + table + " WHERE entity_type = :et AND entity_id IN (:ids)");
            q.setParameter("et", entityType);
            q.setParameter("ids", ids);
            q.executeUpdate();
        }
    }

    private int deleteAttachmentDirs(UUID orgId, List<UUID> incidentIds) {
        int removed = 0;
        for (UUID incidentId : incidentIds) {
            Path dir = Paths.get(storePath, orgId.toString(), incidentId.toString());
            if (!Files.exists(dir)) continue;
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        log.warn("Could not delete attachment file {}: {}", p, e.getMessage());
                    }
                });
                removed++;
            } catch (IOException e) {
                log.warn("Could not walk attachment dir {}: {}", dir, e.getMessage());
            }
        }
        return removed;
    }
}
