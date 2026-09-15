package com.alignedcardio.itsm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurgeDeletedServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final UUID INCIDENT_ID = UUID.randomUUID();
    private static final UUID SR_ID = UUID.randomUUID();

    @Mock private EntityManager em;

    @TempDir Path storeDir;

    private PurgeDeletedService service;
    private final List<String> executedSql = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new PurgeDeletedService(em, storeDir.toString());
        lenient().when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            executedSql.add(sql);
            Query q = mock(Query.class);
            lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
            if (sql.startsWith("SELECT id")) {
                List<UUID> ids = sql.contains("FROM incident") ? List.of(INCIDENT_ID) : List.of(SR_ID);
                when(q.getResultList()).thenReturn(new ArrayList<>(ids));
            } else if (sql.startsWith("SELECT COUNT")) {
                when(q.getSingleResult()).thenReturn(3L);
            } else {
                lenient().when(q.executeUpdate()).thenReturn(3);
            }
            return q;
        });
    }

    @Test
    void dryRunReturnsCountsWithoutDeleting() {
        PurgeDeletedService.PurgeResult result = service.purge(ORG_ID, true);

        assertTrue(result.dryRun());
        assertEquals(1, result.incidentsPurged());
        assertEquals(1, result.serviceRequestsPurged());
        assertEquals(3L, result.counts().get("incident_comment"));
        assertEquals(3L, result.counts().get("sla_instance(service_request)"));
        assertEquals(3L, result.counts().get("audit_log(INCIDENT)"));
        assertEquals(3L, result.counts().get("time_entry(SERVICE_REQUEST)"));
        assertEquals(1L, result.counts().get("incident"));
        assertTrue(executedSql.stream().noneMatch(s -> s.startsWith("DELETE")));
    }

    @Test
    void purgeDeletesChildrenBeforeParents() {
        service.purge(ORG_ID, false);

        int parentIncident = executedSql.indexOf("DELETE FROM incident WHERE id IN (:ids)");
        int parentSr = executedSql.indexOf("DELETE FROM service_request WHERE id IN (:ids)");
        assertTrue(parentIncident > 0);
        assertTrue(parentSr > 0);
        for (String sql : executedSql) {
            if (!sql.startsWith("DELETE")) continue;
            if (sql.equals("DELETE FROM incident WHERE id IN (:ids)")) continue;
            if (sql.equals("DELETE FROM service_request WHERE id IN (:ids)")) continue;
            int idx = executedSql.indexOf(sql);
            assertTrue(idx < parentIncident && idx < parentSr,
                    "Child delete ran after parents: " + sql);
        }
    }

    @Test
    void purgeCoversAllChildAndPolymorphicTables() {
        service.purge(ORG_ID, false);

        List<String> deletes = executedSql.stream().filter(s -> s.startsWith("DELETE")).toList();
        for (String expected : new String[]{
                "incident_link", "problem_incident_link", "incident_watcher", "incident_comment",
                "incident_attachment", "sla_instance", "fulfillment_task", "service_request_comment",
                "automation_run_log", "audit_log", "notification", "time_entry", "incident", "service_request"}) {
            assertTrue(deletes.stream().anyMatch(s -> s.contains("FROM " + expected + " ")),
                    "Missing delete for table " + expected);
        }
        // incident_link must match both link directions
        assertTrue(deletes.stream().anyMatch(s -> s.contains("from_incident_id") && s.contains("to_incident_id")));
        // polymorphic deletes must scope by entity_type
        assertTrue(deletes.stream()
                .filter(s -> s.contains("FROM audit_log"))
                .allMatch(s -> s.contains("entity_type")));
    }

    @Test
    void purgeRemovesAttachmentDirectories() throws Exception {
        Path dir = storeDir.resolve(ORG_ID.toString()).resolve(INCIDENT_ID.toString());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file.pdf"), "data");

        PurgeDeletedService.PurgeResult result = service.purge(ORG_ID, false);

        assertEquals(1, result.attachmentDirsRemoved());
        assertFalse(Files.exists(dir));
    }

    @Test
    void purgeWithNoSoftDeletedRowsDoesNothing() {
        when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            executedSql.add(sql);
            Query q = mock(Query.class);
            when(q.setParameter(anyString(), any())).thenReturn(q);
            when(q.getResultList()).thenReturn(new ArrayList<>());
            return q;
        });

        PurgeDeletedService.PurgeResult result = service.purge(ORG_ID, false);

        assertEquals(0, result.incidentsPurged());
        assertEquals(0, result.serviceRequestsPurged());
        assertTrue(executedSql.stream().noneMatch(s -> s.startsWith("DELETE")));
    }
}
