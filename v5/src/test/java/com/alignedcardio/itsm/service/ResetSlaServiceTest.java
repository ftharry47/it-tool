package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ServiceRequest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResetSlaServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock private EntityManager em;
    @Mock private SlaEngine slaEngine;

    private ResetSlaService service;
    private final List<String> executedSql = new ArrayList<>();

    private Incident openIncident;
    private Incident closedIncident;
    private ServiceRequest openRequest;
    private Problem openProblem;

    @BeforeEach
    void setUp() {
        service = new ResetSlaService(em, slaEngine);

        openIncident = new Incident();
        openIncident.setStatus(Incident.Status.IN_PROGRESS);
        closedIncident = new Incident();
        closedIncident.setStatus(Incident.Status.CLOSED);
        openRequest = new ServiceRequest();
        openRequest.setStatus(ServiceRequest.Status.IN_FULFILLMENT);
        openProblem = new Problem();
        openProblem.setStatus(Problem.Status.INVESTIGATING);

        lenient().when(em.createNativeQuery(anyString())).thenAnswer(inv -> {
            String sql = inv.getArgument(0);
            executedSql.add(sql);
            Query q = mock(Query.class);
            lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
            if (sql.startsWith("SELECT COUNT")) {
                when(q.getSingleResult()).thenReturn(4L);
            } else {
                lenient().when(q.executeUpdate()).thenReturn(16);
            }
            return q;
        });

        stubTickets(Incident.class, List.of(openIncident));
        stubTickets(ServiceRequest.class, List.of(openRequest));
        stubTickets(Problem.class, List.of(openProblem));
        stubTickets(ChangeRequest.class, List.of());
    }

    @SuppressWarnings("unchecked")
    private <T> void stubTickets(Class<T> type, List<T> rows) {
        TypedQuery<T> q = mock(TypedQuery.class);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.getResultList()).thenReturn(rows);
        lenient().when(em.createQuery(anyString(), eq(type))).thenReturn(q);
    }

    @Test
    void dryRunReportsCountsWithoutDeleting() {
        ResetSlaService.ResetResult result = service.reset(ORG_ID, true);

        assertTrue(result.dryRun());
        assertEquals(20, result.totalWiped()); // 4L per row × 5 buckets
        assertEquals(4L, result.wipedByType().get("incident"));
        assertEquals(3, result.totalRecreated()); // 3 open tickets are candidates
        assertTrue(executedSql.stream().noneMatch(s -> s.startsWith("DELETE")));
        verifyNoInteractions(slaEngine);
    }

    @Test
    void commitWipesAndRecreatesOnlyOpenTickets() {
        when(slaEngine.onIncidentCreated(openIncident)).thenReturn(true);
        when(slaEngine.onServiceRequestCreated(openRequest)).thenReturn(true);
        when(slaEngine.onProblemCreated(openProblem)).thenReturn(false); // no matching policy

        ResetSlaService.ResetResult result = service.reset(ORG_ID, false);

        assertFalse(result.dryRun());
        assertTrue(executedSql.stream().anyMatch(s -> s.equals("DELETE FROM sla_instance WHERE org_id = :org")));
        assertEquals(2, result.totalRecreated());
        assertEquals(1L, result.recreatedByType().get("incident"));
        assertEquals(1L, result.recreatedByType().get("service_request"));
        assertEquals(0L, result.recreatedByType().get("problem"));
        verify(slaEngine).onIncidentCreated(openIncident);
        verify(slaEngine).onServiceRequestCreated(openRequest);
        verify(slaEngine).onProblemCreated(openProblem);
        verify(slaEngine, never()).onChangeCreated(any());
        // closed incident was never fetched — terminal statuses filtered in JPQL
    }
}
