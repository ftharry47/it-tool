package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.SlaInstance;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.Mockito;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock
    private EntityManager entityManager;

    @Mock
    private CriteriaBuilder criteriaBuilder;

    @Mock
    private CriteriaQuery<Tuple> criteriaQuery;

    @Mock
    private Root<Incident> root;

    @Mock
    private Path<Object> path;

    @Mock
    private Predicate predicate;

    @Mock
    private Expression<Long> countExpr;

    @Mock
    private TypedQuery<Tuple> typedQuery;

    @Mock
    private Tuple tuple;

    @Test
    void unknownEntityIsRejected() {
        ReportingService service = new ReportingService(entityManager);

        AdHocQueryRequest request = new AdHocQueryRequest(
                "unknown_entity", List.of(), null, null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.adHocQuery(UUID.randomUUID(), request, null));

        assertTrue(ex.getMessage().contains("Unknown report entity"));
    }

    @Test
    void unknownFilterFieldIsRejected() {
        ReportingService service = new ReportingService(entityManager);

        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("injectionField", "eq", "P1")),
                null,
                null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.adHocQuery(UUID.randomUUID(), request, null));

        assertTrue(ex.getMessage().contains("Field not allowed for filter"));
    }

    @Test
    void dateRangeSpanGuardIsEnforced() {
        ReportingService service = new ReportingService(entityManager);

        OffsetDateTime from = OffsetDateTime.now().minusYears(3);
        OffsetDateTime to = OffsetDateTime.now();

        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(),
                null,
                new AdHocQueryRequest.DateRange(from, to));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.adHocQuery(UUID.randomUUID(), request, null));

        assertTrue(ex.getMessage().contains("exceeds maximum span"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void groupedIncidentQueryUsesOrgIdPredicateAndRespectsRowLimit() {
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
        when(criteriaQuery.from(Incident.class)).thenReturn(root);
        when(root.get(anyString())).thenReturn(path);
        when(path.getJavaType()).thenReturn((Class) Incident.Status.class);
        when(criteriaBuilder.count(root)).thenReturn(countExpr);
        lenient().when(criteriaBuilder.equal(any(Path.class), any(Object.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNull(any(Path.class))).thenReturn(predicate);
        when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.groupBy(any(Expression[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(tuple));
        when(tuple.get(0)).thenReturn(Incident.Status.NEW);
        when(tuple.get(1)).thenReturn(5L);

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("status", "eq", "NEW")),
                "status",
                null);

        AdHocQueryResponse response = service.adHocQuery(orgId, request, null);

        assertEquals(orgId, response.orgId());
        assertEquals("incident", response.entity());
        assertEquals("status", response.groupBy());
        assertEquals(1, response.rows().size());

        Map<String, Object> row = response.rows().get(0);
        assertEquals(Incident.Status.NEW.name(), row.get("group"));
        assertEquals(5L, row.get("count"));

        verify(criteriaBuilder).equal(path, orgId);
        verify(criteriaBuilder).equal(path, Incident.Status.NEW);
        verify(criteriaBuilder).isNull(path);
        verify(criteriaQuery).groupBy(any(Expression[].class));
        verify(typedQuery).setMaxResults(1000);
    }

    /**
     * Regression: mine=true must scope problem/change queries by assignee —
     * previously only incident/service_request/issue had a mineField, so an
     * agent's "mine" query on problem/change silently returned org-wide rows.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void mineScopeAppliesToProblemAndChangeAssignee() {
        Root rawRoot = mock(Root.class);
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
        doReturn(rawRoot).when(criteriaQuery).from(any(Class.class));
        when(rawRoot.get(anyString())).thenReturn(path);
        when(path.get(anyString())).thenReturn(path);
        lenient().when(criteriaBuilder.equal(any(Expression.class), any(Object.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNull(any(Expression.class))).thenReturn(predicate);
        when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.multiselect(any(Selection.class))).thenReturn(criteriaQuery);
        when(criteriaBuilder.count(rawRoot)).thenReturn(countExpr);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of());

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();
        UUID agentId = UUID.randomUUID();

        service.adHocQuery(orgId, new AdHocQueryRequest("problem", List.of(), null, null), agentId);
        verify(criteriaBuilder).equal(path, agentId);
        verify(criteriaQuery).from(Problem.class);

        clearInvocations(criteriaBuilder, criteriaQuery);

        service.adHocQuery(orgId, new AdHocQueryRequest("change", List.of(), null, null), agentId);
        verify(criteriaBuilder).equal(path, agentId);
        verify(criteriaQuery).from(ChangeRequest.class);
    }

    /**
     * Regression: SLA instance filters must LEFT-join the linked tickets. The
     * previous implicit inner joins dropped every row whose other FK was null —
     * all SR-backed instances vanished from compliance numbers.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slaComplianceUsesLeftJoinsForLinkedTickets() {
        EntityManager em = mock(EntityManager.class, Mockito.RETURNS_DEEP_STUBS);
        when(em.createQuery(any(CriteriaQuery.class)).getSingleResult()).thenReturn(0L);

        ReportingService service = new ReportingService(em);
        service.slaCompliance(UUID.randomUUID(), null);

        CriteriaQuery<Long> q = em.getCriteriaBuilder().createQuery(Long.class);
        Root<SlaInstance> root = q.from(SlaInstance.class);
        verify(root, atLeastOnce()).join("incident", JoinType.LEFT);
        verify(root, atLeastOnce()).join("serviceRequest", JoinType.LEFT);
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacySplitAggregatesTotalsAndPerCategoryBreakdown() {
        EntityManager em = mock(EntityManager.class);
        TypedQuery<Tuple> totalsQuery = mock(TypedQuery.class);
        TypedQuery<Tuple> categoryQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("SELECT i.legacyImport"), eq(Tuple.class))).thenReturn(totalsQuery);
        when(em.createQuery(contains("SELECT COALESCE(c.name"), eq(Tuple.class))).thenReturn(categoryQuery);
        when(totalsQuery.setParameter(anyString(), any())).thenReturn(totalsQuery);
        when(categoryQuery.setParameter(anyString(), any())).thenReturn(categoryQuery);

        Tuple legacyTotal = mock(Tuple.class);
        when(legacyTotal.get(0, Boolean.class)).thenReturn(true);
        when(legacyTotal.get(1, Long.class)).thenReturn(40L);
        Tuple currentTotal = mock(Tuple.class);
        when(currentTotal.get(0, Boolean.class)).thenReturn(false);
        when(currentTotal.get(1, Long.class)).thenReturn(10L);
        when(totalsQuery.getResultList()).thenReturn(List.of(legacyTotal, currentTotal));

        Tuple hwLegacy = mock(Tuple.class);
        when(hwLegacy.get(0, String.class)).thenReturn("Hardware");
        when(hwLegacy.get(1, Boolean.class)).thenReturn(true);
        when(hwLegacy.get(2, Long.class)).thenReturn(30L);
        Tuple hwCurrent = mock(Tuple.class);
        when(hwCurrent.get(0, String.class)).thenReturn("Hardware");
        when(hwCurrent.get(1, Boolean.class)).thenReturn(false);
        when(hwCurrent.get(2, Long.class)).thenReturn(5L);
        when(categoryQuery.getResultList()).thenReturn(List.of(hwLegacy, hwCurrent));

        ReportingService service = new ReportingService(em);
        Map<String, Object> result = service.legacySplit(UUID.randomUUID(), null);

        assertEquals(40L, result.get("legacy"));
        assertEquals(10L, result.get("current"));
        List<Map<String, Object>> categories = (List<Map<String, Object>>) result.get("byCategory");
        assertEquals(1, categories.size());
        assertEquals("Hardware", categories.get(0).get("category"));
        assertEquals(30L, categories.get(0).get("legacy"));
        assertEquals(5L, categories.get(0).get("current"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void ticketsMonthlyBucketsCreatedAndClosedByMonth() {
        EntityManager em = mock(EntityManager.class, Mockito.RETURNS_DEEP_STUBS);

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime lastMonth = now.minusMonths(1);
        Tuple t1 = mock(Tuple.class);
        when(t1.get(0, OffsetDateTime.class)).thenReturn(now);
        when(t1.get(1, OffsetDateTime.class)).thenReturn(now);
        Tuple t2 = mock(Tuple.class);
        when(t2.get(0, OffsetDateTime.class)).thenReturn(lastMonth);
        when(t2.get(1, OffsetDateTime.class)).thenReturn(now);
        when(em.createQuery(any(CriteriaQuery.class)).getResultList()).thenReturn(List.of(t1, t2));

        ReportingService service = new ReportingService(em);
        List<Map<String, Object>> result = service.ticketsMonthly(UUID.randomUUID(), 3, null);

        assertEquals(3, result.size());
        Map<String, Object> thisMonth = result.get(2);
        Map<String, Object> prevMonth = result.get(1);
        assertEquals(1L, thisMonth.get("created"));  // only t1 created this month
        assertEquals(2L, thisMonth.get("closed"));   // both closed this month
        assertEquals(1L, prevMonth.get("created"));  // t2 created last month
    }

    @Test
    void ticketsMonthlyRejectsInvalidMonthRange() {
        ReportingService service = new ReportingService(mock(EntityManager.class));
        assertThrows(IllegalArgumentException.class,
                () -> service.ticketsMonthly(UUID.randomUUID(), 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.ticketsMonthly(UUID.randomUUID(), 200, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void ticketsByLocationWithNullDateRangeDoesNotThrowAndDoesNotBindNullParameters() {
        doReturn(typedQuery).when(entityManager).createQuery(anyString(), any(Class.class));
        lenient().when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        lenient().when(typedQuery.getResultStream()).thenReturn(Stream.empty());
        lenient().when(typedQuery.getResultList()).thenReturn(List.of());

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        List<?> result = assertDoesNotThrow(
                () -> service.ticketsByLocation(orgId, "OPEN", null, null));

        assertNotNull(result);
        verify(typedQuery, never()).setParameter(anyString(), isNull());
    }
}
