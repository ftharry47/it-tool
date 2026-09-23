package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.Team;
import org.mockito.ArgumentCaptor;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.service.SupportTiers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
                "unknown_entity", List.of(), null, null, null, null, null);

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
                null, null, null, null);

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
                new AdHocQueryRequest.DateRange(from, to, null),
                null, null, null);

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
        when(tuple.get(1)).thenReturn(Incident.Status.NEW); // groupKey — raw value for drill-down
        when(tuple.get(2)).thenReturn(5L);

        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();

        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("status", "eq", "NEW")),
                "status",
                null, null, null, null);

        AdHocQueryResponse response = service.adHocQuery(orgId, request, null);

        assertEquals(orgId, response.orgId());
        assertEquals("incident", response.entity());
        assertEquals("status", response.groupBy());
        assertEquals(1, response.rows().size());

        Map<String, Object> row = response.rows().get(0);
        assertEquals(Incident.Status.NEW.name(), row.get("group"));
        assertEquals(Incident.Status.NEW.name(), row.get("groupKey"));
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

        service.adHocQuery(orgId, new AdHocQueryRequest("problem", List.of(), null, null, null, null, null), agentId);
        verify(criteriaBuilder).equal(path, agentId);
        verify(criteriaQuery).from(Problem.class);

        clearInvocations(criteriaBuilder, criteriaQuery);

        service.adHocQuery(orgId, new AdHocQueryRequest("change", List.of(), null, null, null, null, null), agentId);
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

    /**
     * Detailed mode: real rows with a working detailUrl, plus a total count —
     * previously only aggregate counts were possible.
     */
    @Test
    @SuppressWarnings("unchecked")
    void detailedQueryReturnsRealRowsWithDetailUrlAndTotal() {
        EntityManager em = mock(EntityManager.class, Mockito.RETURNS_DEEP_STUBS);

        // Count query vs select query resolve to distinct CriteriaQuery mocks.
        CriteriaQuery<Long> countQ = em.getCriteriaBuilder().createQuery(Long.class);
        CriteriaQuery<Object> selectQ = em.getCriteriaBuilder().createQuery(Object.class);
        TypedQuery<Long> countTyped = mock(TypedQuery.class);
        TypedQuery<Object> selectTyped = mock(TypedQuery.class);
        when(em.createQuery(countQ)).thenReturn(countTyped);
        when(em.createQuery(selectQ)).thenReturn(selectTyped);
        when(countTyped.getSingleResult()).thenReturn(7L);
        when(selectTyped.setFirstResult(anyInt())).thenReturn(selectTyped);
        when(selectTyped.setMaxResults(anyInt())).thenReturn(selectTyped);

        // Field type for the status filter — deep stubs return null otherwise.
        Root<?> selectRoot = selectQ.from(Incident.class);
        Root<?> countRoot = countQ.from(Incident.class);
        Path<Object> anyPath = mock(Path.class);
        when(selectRoot.get(anyString())).thenReturn(anyPath);
        when(countRoot.get(anyString())).thenReturn(anyPath);
        when(anyPath.getJavaType()).thenAnswer(inv -> String.class);

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setNumber(42L);
        incident.setTitle("VPN drops");
        incident.setStatus(Incident.Status.IN_PROGRESS);
        incident.setCreatedAt(OffsetDateTime.now());
        when(selectTyped.getResultList()).thenReturn(List.of(incident));

        ReportingService service = new ReportingService(em);
        AdHocQueryRequest request = new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("status", "eq", "IN_PROGRESS")),
                null, null, true, 0, 50);

        AdHocQueryResponse response = service.adHocQuery(UUID.randomUUID(), request, null);

        assertEquals(7L, response.total());
        assertEquals(0, response.page());
        assertEquals(1, response.rows().size());
        Map<String, Object> row = response.rows().get(0);
        assertEquals("INC-42", row.get("ref"));
        assertEquals("VPN drops", row.get("title"));
        assertEquals("IN_PROGRESS", row.get("status"));
        assertEquals("/dashboard/incidents/" + incident.getId(), row.get("detailUrl"));
        verify(selectTyped).setFirstResult(0);
        verify(selectTyped).setMaxResults(50);
    }

    /**
     * Entity-valued filters accept display names ("Critical") not just UUIDs —
     * required for templates like "Open Critical Incidents".
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void entityFilterAcceptsNameNotJustUuid() {
        EntityManager em = mock(EntityManager.class, Mockito.RETURNS_DEEP_STUBS);
        CriteriaQuery<Object> selectQ = em.getCriteriaBuilder().createQuery(Object.class);
        TypedQuery<Object> selectTyped = mock(TypedQuery.class);
        when(em.createQuery(any(CriteriaQuery.class))).thenReturn(selectTyped);
        when(selectTyped.setFirstResult(anyInt())).thenReturn(selectTyped);
        when(selectTyped.setMaxResults(anyInt())).thenReturn(selectTyped);
        when(selectTyped.getResultList()).thenReturn(List.of());
        when(selectTyped.getSingleResult()).thenReturn(0L);

        // The "priority" filter resolves to a Priority entity type → name matching.
        CriteriaQuery<Long> countQ = em.getCriteriaBuilder().createQuery(Long.class);
        Root<?> selectRoot = selectQ.from(Incident.class);
        Root<?> countRoot = countQ.from(Incident.class);
        Path<Object> anyPath = mock(Path.class);
        when(selectRoot.get(anyString())).thenReturn(anyPath);
        when(countRoot.get(anyString())).thenReturn(anyPath);
        when(anyPath.getJavaType()).thenReturn((Class) com.alignedcardio.itsm.entity.Priority.class);
        lenient().when(anyPath.get(anyString())).thenReturn(anyPath);
        lenient().doReturn(anyPath).when(anyPath).as(any(Class.class));

        ReportingService service = new ReportingService(em);
        service.adHocQuery(UUID.randomUUID(), new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("priority", "eq", "Critical")),
                null, null, true, 0, 50), null);

        // Name-based match: lower(name) = 'critical' — no UUID lookup attempted.
        verify(em.getCriteriaBuilder(), atLeastOnce()).lower(any());
        verify(em, never()).getReference(eq(com.alignedcardio.itsm.entity.Priority.class), any());
    }

    /**
     * "Tickets I Worked On" is audit-driven: a ticket the agent acted on shows
     * up even when it was created outside the range.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void workedTicketsSurfaceOldTicketsWorkedInRange() {
        EntityManager em = mock(EntityManager.class);
        TypedQuery<Tuple> auditQuery = mock(TypedQuery.class);
        TypedQuery<Incident> incidentQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("FROM AuditLog"), eq(Tuple.class))).thenReturn(auditQuery);
        when(em.createQuery(contains("FROM Incident i"), eq(Incident.class))).thenReturn(incidentQuery);
        when(auditQuery.setParameter(anyString(), any())).thenReturn(auditQuery);
        when(incidentQuery.setParameter(anyString(), any())).thenReturn(incidentQuery);

        UUID agentId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        OffsetDateTime workedAt = OffsetDateTime.now().minusDays(2);
        Tuple t = mock(Tuple.class);
        when(t.get(0, String.class)).thenReturn("INCIDENT");
        when(t.get(1, UUID.class)).thenReturn(incidentId);
        when(t.get(2, OffsetDateTime.class)).thenReturn(workedAt);
        when(auditQuery.getResultList()).thenReturn(List.of(t));

        Incident old = new Incident();
        old.setId(incidentId);
        old.setNumber(9L);
        old.setTitle("Old ticket, recently worked");
        old.setStatus(Incident.Status.IN_PROGRESS);
        old.setCreatedAt(OffsetDateTime.now().minusMonths(6)); // created long before the range
        when(incidentQuery.getResultList()).thenReturn(List.of(old));

        ReportingService service = new ReportingService(em);
        OffsetDateTime from = OffsetDateTime.now().minusDays(7);
        OffsetDateTime to = OffsetDateTime.now();
        List<Map<String, Object>> rows = service.agentPerformanceTickets(
                UUID.randomUUID(), List.of(agentId), from, to, null, null);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("INCIDENT", row.get("type"));
        assertEquals("INC-9", row.get("number"));
        assertEquals(workedAt, row.get("workedAt"));
        // The audit query — not a createdAt filter — decided inclusion.
        verify(auditQuery).setParameter("from", from);
        verify(auditQuery).setParameter("agents", List.of(agentId));
    }

    /** SLA compliance grouped by agent, with fulfiller attribution for SRs. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slaComplianceByDimensionGroupsByAgent() {
        EntityManager em = mock(EntityManager.class);
        TypedQuery<SlaInstance> instanceQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("FROM SlaInstance"), eq(SlaInstance.class))).thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);

        com.alignedcardio.itsm.entity.AppUser alice = new com.alignedcardio.itsm.entity.AppUser();
        alice.setDisplayName("Alice Agent");
        Incident breached = new Incident();
        breached.setAssignee(alice);
        breached.setStatus(Incident.Status.IN_PROGRESS);
        Incident ok = new Incident();
        ok.setAssignee(alice);
        ok.setStatus(Incident.Status.IN_PROGRESS);

        SlaInstance siBreached = new SlaInstance();
        siBreached.setIncident(breached);
        siBreached.setResolutionDueAt(OffsetDateTime.now().minusHours(1));
        SlaInstance siOk = new SlaInstance();
        siOk.setIncident(ok);
        siOk.setResolutionDueAt(OffsetDateTime.now().plusDays(1));
        when(instanceQuery.getResultList()).thenReturn(List.of(siBreached, siOk));

        ReportingService service = new ReportingService(em);
        List<Map<String, Object>> rows = service.slaComplianceByDimension(
                UUID.randomUUID(), "incident", "agent");

        assertEquals(1, rows.size());
        assertEquals("Alice Agent", rows.get(0).get("name"));
        assertEquals(2L, rows.get(0).get("total"));
        assertEquals(1L, rows.get(0).get("breached"));
        assertEquals(50.0, rows.get(0).get("compliancePercent"));
    }

    /** Cancelled SRs are excluded from the SLA dimension report. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slaComplianceByDimensionExcludesCancelledRequests() {
        EntityManager em = mock(EntityManager.class);
        TypedQuery<SlaInstance> instanceQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("FROM SlaInstance"), eq(SlaInstance.class))).thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.getResultList()).thenReturn(List.of());

        ReportingService service = new ReportingService(em);
        service.slaComplianceByDimension(UUID.randomUUID(), "service_request", "agent");

        verify(instanceQuery).setParameter("cancelled",
                com.alignedcardio.itsm.entity.ServiceRequest.Status.CANCELLED);
    }

    // --- SLA Compliance Breakdown verification (overall / team / agent) ---

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void slaComplianceBreakdownComputesOverallTeamAndAgent() {
        UUID orgId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        AppUser alice = mock(AppUser.class);
        lenient().when(alice.getId()).thenReturn(aliceId);
        lenient().when(alice.getDisplayName()).thenReturn("Alice");
        Team l1 = mock(Team.class);
        lenient().when(l1.getName()).thenReturn("L1 Support");
        Incident incBreached = mock(Incident.class);
        lenient().when(incBreached.getAssignee()).thenReturn(alice);
        lenient().when(incBreached.getAssignmentTeam()).thenReturn(l1);
        lenient().when(incBreached.getDeletedAt()).thenReturn(null);
        Incident incOk = mock(Incident.class);
        lenient().when(incOk.getAssignee()).thenReturn(alice);
        lenient().when(incOk.getAssignmentTeam()).thenReturn(l1);
        lenient().when(incOk.getDeletedAt()).thenReturn(null);

        UUID srId = UUID.randomUUID();
        ServiceRequest sr = mock(ServiceRequest.class);
        lenient().when(sr.getId()).thenReturn(srId);
        lenient().when(sr.getDeletedAt()).thenReturn(null);
        lenient().when(sr.getRequester()).thenReturn(null);

        // Instance 1: breached (met after due). 2: compliant (met before due).
        // 3: SR instance — ownership via fulfillment task assignee (Bob).
        SlaInstance siBreached = mock(SlaInstance.class);
        lenient().when(siBreached.getIncident()).thenReturn(incBreached);
        lenient().when(siBreached.getResolutionDueAt()).thenReturn(now.minusHours(2));
        lenient().when(siBreached.getResolutionMetAt()).thenReturn(now.minusHours(1));
        SlaInstance siOk = mock(SlaInstance.class);
        lenient().when(siOk.getIncident()).thenReturn(incOk);
        lenient().when(siOk.getResolutionDueAt()).thenReturn(now.plusHours(5));
        lenient().when(siOk.getResolutionMetAt()).thenReturn(now);
        SlaInstance siSr = mock(SlaInstance.class);
        lenient().when(siSr.getServiceRequest()).thenReturn(sr);
        lenient().when(siSr.getResolutionDueAt()).thenReturn(now.plusHours(5));
        lenient().when(siSr.getResolutionMetAt()).thenReturn(now);

        TypedQuery<SlaInstance> siQ = mock(TypedQuery.class);
        lenient().when(siQ.getResultList()).thenReturn(List.of(siBreached, siOk, siSr));
        lenient().when(siQ.setParameter(anyString(), any())).thenReturn(siQ);
        lenient().when(entityManager.createQuery(
                contains("FROM SlaInstance si "), eq(SlaInstance.class)))
                .thenReturn(siQ);

        stubJpql(Map.of(
                "FROM FulfillmentTask ft JOIN ft.assignee a", List.of(
                        tuple(srId, bobId, "Bob")),
                "FROM TeamMember tm JOIN tm.team t", List.of(
                        tuple(aliceId, "L1 Support"),
                        tuple(bobId, "L2 Support"))));

        ReportingService service = new ReportingService(entityManager);
        Map<String, Object> result = service.slaComplianceBreakdown(orgId);

        // N+1 regression: the instance query must JOIN FETCH the ticket
        // associations instead of lazy-loading each row.
        verify(entityManager).createQuery(contains("JOIN FETCH si.incident"), eq(SlaInstance.class));

        // Overall: 3 SLAs, 1 breached → 66.67%.
        @SuppressWarnings("unchecked")
        Map<String, Object> overall = (Map<String, Object>) result.get("overall");
        assertEquals(3L, overall.get("total"));
        assertEquals(1L, overall.get("breached"));
        assertEquals(66.67, overall.get("compliancePercent"));

        // By agent: Alice 2 total / 1 breached (50%), Bob 1 / 0 (100%).
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byAgent = (List<Map<String, Object>>) result.get("byAgent");
        Map<String, Object> aliceRow = byAgent.stream()
                .filter(r -> "Alice".equals(r.get("agentName"))).findFirst().orElseThrow();
        assertEquals(2L, aliceRow.get("total"));
        assertEquals(1L, aliceRow.get("breached"));
        assertEquals(50.0, aliceRow.get("compliancePercent"));
        Map<String, Object> bobRow = byAgent.stream()
                .filter(r -> "Bob".equals(r.get("agentName"))).findFirst().orElseThrow();
        assertEquals(1L, bobRow.get("total"));
        assertEquals(100.0, bobRow.get("compliancePercent"));

        // By team: L1 2/1, L2 1/0.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> byTeam = (List<Map<String, Object>>) result.get("byTeam");
        Map<String, Object> l1Row = byTeam.stream()
                .filter(r -> "L1 Support".equals(r.get("teamName"))).findFirst().orElseThrow();
        assertEquals(2L, l1Row.get("total"));
        assertEquals(50.0, l1Row.get("compliancePercent"));
        Map<String, Object> l2Row = byTeam.stream()
                .filter(r -> "L2 Support".equals(r.get("teamName"))).findFirst().orElseThrow();
        assertEquals(1L, l2Row.get("total"));
        assertEquals(100.0, l2Row.get("compliancePercent"));
    }

    // --- Parts D/E/F: union export, location dashboard, workload detail ---

    /** Dispatches string-JPQL Tuple queries by content fragment. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubJpql(Map<String, List<Tuple>> responses) {
        lenient().when(entityManager.createQuery(anyString(), eq(Tuple.class))).thenAnswer(inv -> {
            String jpql = inv.getArgument(0);
            TypedQuery<Tuple> q = mock(TypedQuery.class);
            List<Tuple> result = List.of();
            for (Map.Entry<String, List<Tuple>> e : responses.entrySet()) {
                if (jpql.contains(e.getKey())) {
                    result = e.getValue();
                    break;
                }
            }
            lenient().when(q.getResultList()).thenReturn(result);
            lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
            lenient().when(q.setParameter(anyInt(), any())).thenReturn(q);
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private Tuple tuple(Object... values) {
        Tuple t = mock(Tuple.class);
        lenient().when(t.get(anyInt())).thenAnswer(inv -> values[(int) inv.getArgument(0)]);
        lenient().when(t.get(anyInt(), any(Class.class))).thenAnswer(
                inv -> values[(int) inv.getArgument(0)]);
        return t;
    }

    @Test
    void fullDetailExportUnionsIncidentsAndServiceRequests() {
        UUID orgId = UUID.randomUUID();
        UUID incId = UUID.randomUUID();
        UUID srId = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Escalated incident: SLA met 2h after due → breached with a
        // breachDurationMinutes value; two escalation events in the trail.
        Map<String, List<Tuple>> responses = new LinkedHashMap<>();
        // 0 incId | 1 srId | 2 breachStatus | 3 responseMetAt | 4 responseDueAt
        // | 5 resolutionDueAt | 6 resolutionMetAt | 7 escalationLevel | 8 policy
        responses.put("SELECT si.incident.id", List.of(
                tuple(incId, null, SlaInstance.BreachStatus.BREACHED, now,
                        now.minusHours(6), now.minusHours(4), now.minusHours(2),
                        2, "Standard Resolution")));
        responses.put("FROM Incident i", List.of(
                tuple(incId, 42L, "VPN down", Incident.Status.IN_PROGRESS,
                        "High", "Network", "HQ", "Alice", "Bob", now, now)));
        responses.put("FROM ServiceRequest s", List.of(
                tuple(srId, "SR-7", "Laptop Request", ServiceRequest.Status.FULFILLED,
                        "Medium", "HQ", "Carol", "Dan", now, now)));
        // lastWorkedBy query vs escalation-history query — distinct fragments.
        responses.put("a.actorUserId", List.of(
                tuple("INCIDENT", incId, actor),
                tuple("SERVICE_REQUEST", srId, actor)));
        responses.put("a.action, a.detail", List.of(
                tuple("INCIDENT", incId, "ESCALATE_TIER", "L1 → L2", now.minusHours(5)),
                tuple("INCIDENT", incId, "AUTO_ESCALATE_TIER", "L2 → L3", now.minusHours(3))));
        responses.put("FROM AppUser u", List.of(tuple(actor, "Eve")));
        stubJpql(responses);

        ReportingService service = new ReportingService(entityManager);
        Map<String, List<Map<String, Object>>> sheets = service.fullDetailExport(
                orgId, now.minusDays(30), now.plusDays(1));

        // Two separate row sets — no type discriminator.
        List<Map<String, Object>> incidentRows = sheets.get("incidents");
        List<Map<String, Object>> requestRows = sheets.get("serviceRequests");
        assertEquals(1, incidentRows.size());
        assertEquals(1, requestRows.size());
        assertNull(incidentRows.get(0).get("type"));

        Map<String, Object> inc = incidentRows.get(0);
        assertEquals("INC-42", inc.get("number"));
        assertEquals("BREACHED", inc.get("slaStatus"));
        assertEquals("Eve", inc.get("lastWorkedBy"));
        // SLA detail columns
        assertEquals("Standard Resolution", inc.get("slaPolicy"));
        assertEquals(120L, inc.get("breachDurationMinutes")); // met 2h past due
        assertEquals(2, inc.get("escalationLevel"));
        assertEquals(2, inc.get("escalationCount"));
        String hist = (String) inc.get("escalationHistory");
        assertTrue(hist.contains("ESCALATE_TIER — L1 → L2"), hist);
        assertTrue(hist.contains("AUTO_ESCALATE_TIER — L2 → L3"), hist);
        assertTrue(hist.indexOf("ESCALATE_TIER") < hist.indexOf("AUTO_ESCALATE_TIER"),
                "escalation history should be chronological");

        Map<String, Object> sr = requestRows.get(0);
        assertEquals("SR-7", sr.get("number"));
        assertEquals("Laptop Request", sr.get("catalogItem"));
        assertEquals("Carol", sr.get("approver"));
        // No SLA instance for the SR → blank SLA columns, zero escalation.
        assertEquals("", sr.get("slaStatus"));
        assertEquals(0, sr.get("escalationCount"));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void locationDashboardAggregatesAllFourMetrics() {
        UUID orgId = UUID.randomUUID();
        UUID incId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        // Location names come back as String queries.
        TypedQuery<String> locNames = mock(TypedQuery.class);
        lenient().when(locNames.getResultList()).thenReturn(List.of("HQ"));
        lenient().when(locNames.setParameter(anyString(), any())).thenReturn(locNames);
        lenient().when(entityManager.createQuery(
                contains("SELECT l.name FROM Location"), eq(String.class)))
                .thenReturn(locNames);

        // LinkedHashMap — fragment order matters (more specific first):
        // the SR resolved query also contains "s.status IN :statuses".
        Map<String, List<Tuple>> responses = new LinkedHashMap<>();
        responses.put("i.id IN :ids", List.of(tuple("HQ", 1L)));
        responses.put("s.id IN :ids", List.of(tuple("HQ", 2L)));
        responses.put("i.resolvedAt >= :from", List.of(tuple("HQ", 4L)));
        responses.put("s.updatedAt >= :from", List.of(tuple("HQ", 2L)));
        responses.put("i.status IN :statuses", List.of(tuple("HQ", 3L)));
        responses.put("s.status IN :statuses", List.of(tuple("HQ", 1L)));
        responses.put("i.createdAt >= :from", List.of(tuple("HQ", 5L)));
        responses.put("GROUP BY a.entityType", List.of(
                tuple("INCIDENT", incId),
                tuple("SERVICE_REQUEST", UUID.randomUUID())));
        stubJpql(responses);

        ReportingService service = new ReportingService(entityManager);
        List<Map<String, Object>> rows = service.locationDashboard(
                orgId, now.minusDays(30), now.plusDays(1));

        Map<String, Object> hq = rows.stream()
                .filter(r -> "HQ".equals(r.get("location"))).findFirst().orElseThrow();
        assertEquals(5L, hq.get("opened"));      // incidents only (SR query unmatched → HQ fragment shared)
        assertEquals(3L, hq.get("workedOn"));    // 1 incident + 2 requests
        assertEquals(4L, hq.get("pending"));     // 3 incidents + 1 request
        assertEquals(6L, hq.get("resolved"));    // 4 incidents + 2 requests
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void agentWorkloadDetailIncludesZeroWorkMembers() {
        UUID orgId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();
        UUID bobId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        TypedQuery<Team> teamQ = mock(TypedQuery.class);
        Team l1 = mock(Team.class);
        lenient().when(l1.getId()).thenReturn(SupportTiers.L1_ID);
        lenient().when(l1.getName()).thenReturn("L1 Support");
        lenient().when(teamQ.getResultList()).thenReturn(List.of(l1));
        lenient().when(teamQ.setParameter(anyString(), any())).thenReturn(teamQ);
        lenient().when(entityManager.createQuery(
                contains("SELECT t FROM Team t"), eq(Team.class))).thenReturn(teamQ);

        // Alice (L1) has work; Bob (L1) is a member with zero work.
        // LinkedHashMap — fragment order matters (more specific first).
        Map<String, List<Tuple>> responses = new LinkedHashMap<>();
        responses.put("FROM TeamMember tm JOIN tm.user u", List.of(
                tuple(SupportTiers.L1_ID, aliceId, "Alice"),
                tuple(SupportTiers.L1_ID, bobId, "Bob")));
        responses.put("SUM(CASE WHEN si.breachStatus", List.of(tuple(aliceId, 4L, 1L)));
        responses.put("si.breachStatus = :breached", List.of(tuple(aliceId, 1L)));
        responses.put("i.status IN :statuses", List.of(tuple(aliceId, 3L)));
        responses.put("ft.status IN :statuses", List.of(tuple(aliceId, 1L)));
        responses.put("FROM AuditLog a", List.of(tuple(aliceId, 7L)));
        responses.put("i.resolvedAt >= :from", List.of(tuple(aliceId, 2L)));
        responses.put("ft.deliveredAt >= :from", List.of(tuple(aliceId, 1L)));
        responses.put("ft.expectedDeliveryDate < :today", List.of(tuple(aliceId, 1L)));
        stubJpql(responses);

        ReportingService service = new ReportingService(entityManager);
        List<Map<String, Object>> tiers = service.agentWorkloadDetail(
                orgId, now.minusDays(30), now.plusDays(1));

        assertEquals(4, tiers.size());
        Map<String, Object> l1tier = tiers.get(0);
        assertEquals("L1 Support", l1tier.get("team"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> members =
                (List<Map<String, Object>>) l1tier.get("members");
        assertEquals(2, members.size());
        Map<String, Object> alice = members.stream()
                .filter(m -> "Alice".equals(m.get("name"))).findFirst().orElseThrow();
        assertEquals(4L, alice.get("openAssigned"));   // 3 incidents + 1 task
        assertEquals(7L, alice.get("worked"));
        assertEquals(3L, alice.get("resolved"));       // 2 incidents + 1 task
        assertEquals(2L, alice.get("overdue"));        // 1 breached + 1 overdue task
        assertEquals(75.0, alice.get("slaPercent"));   // 3/4 compliant
        // Bob: member row exists with all zeros — the Part L zero-work gap.
        Map<String, Object> bob = members.stream()
                .filter(m -> "Bob".equals(m.get("name"))).findFirst().orElseThrow();
        assertEquals(0L, bob.get("openAssigned"));
        assertEquals(0L, bob.get("worked"));
        assertEquals(null, bob.get("slaPercent"));
        // Empty tiers still render.
        assertEquals("IT Fulfillment",
                ((Map<?, ?>) tiers.get(3)).get("team"));
    }

    @Test
    void slaComplianceMonthlyByPriorityBreaksDownPerPriority() {
        UUID orgId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        String thisMonth = YearMonth.now().toString();

        stubJpql(Map.of(
                "FROM SlaInstance si", List.of(
                        // High incident, breached (met after due)
                        tuple(now, now.minusHours(1), now, "High", null),
                        // Critical service request, compliant
                        tuple(now, now.plusHours(1), now, null, "Critical"))));

        ReportingService service = new ReportingService(entityManager);
        List<Map<String, Object>> rows = service.slaComplianceMonthlyByPriority(orgId, 3);

        assertEquals(2, rows.size());
        Map<String, Object> critical = rows.stream()
                .filter(r -> "Critical".equals(r.get("priority"))).findFirst().orElseThrow();
        assertEquals(thisMonth, critical.get("month"));
        assertEquals(1L, critical.get("total"));
        assertEquals(0L, critical.get("breached"));
        assertEquals(100.0, critical.get("compliancePercent"));
        Map<String, Object> high = rows.stream()
                .filter(r -> "High".equals(r.get("priority"))).findFirst().orElseThrow();
        assertEquals(1L, high.get("breached"));
        assertEquals(0.0, high.get("compliancePercent"));
    }

    // --- Part N: every Query Builder template must run end-to-end ---------

    /**
     * Mirrors every BUILDER_TEMPLATES entry in AdHocQueryBuilder.tsx (both
     * the original 8 and the 5 added in this batch). Each is run through
     * adHocQuery against a fully-stubbed criteria engine — guards against a
     * repeat of the Part A "No enum constant" breakage where templates
     * shipped without ever being executed.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void everyBuilderTemplateRunsWithoutError() {
        String OPEN_INCIDENT = "NEW,IN_PROGRESS,ON_HOLD,WAITING_ON_CUSTOMER,REOPENED";
        String OPEN_REQUEST = "SUBMITTED,PENDING_APPROVAL,APPROVED,IN_FULFILLMENT,ON_HOLD,REJECTED_NEEDS_REVIEW";

        stubAdHocEngine();
        ReportingService service = new ReportingService(entityManager);
        UUID orgId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime weekAgo = now.minusDays(7);

        record T(String name, String entity, boolean detailed, String groupBy,
                 List<AdHocQueryFilter> filters,
                 AdHocQueryRequest.DateRange range) {}
        List<T> templates = List.of(
                new T("Open Incidents by Agent", "incident", false, "assignee",
                        List.of(new AdHocQueryFilter("status", "in", OPEN_INCIDENT)), null),
                new T("Open Critical Incidents", "incident", true, null,
                        List.of(new AdHocQueryFilter("status", "in", OPEN_INCIDENT),
                                new AdHocQueryFilter("priority", "eq", "Critical")), null),
                new T("Incidents Resolved This Week", "incident", true, null,
                        List.of(new AdHocQueryFilter("status", "in", "RESOLVED,CLOSED")),
                        new AdHocQueryRequest.DateRange(weekAgo, now, "resolvedAt")),
                new T("Pending Approvals Older Than 3 Days", "service_request", true, null,
                        List.of(new AdHocQueryFilter("status", "eq", "PENDING_APPROVAL")),
                        new AdHocQueryRequest.DateRange(now.minusMonths(6), now.minusDays(3), null)),
                new T("Service Requests Awaiting Fulfillment", "service_request", true, null,
                        List.of(new AdHocQueryFilter("status", "in", "APPROVED,IN_FULFILLMENT")), null),
                new T("Reopened Incidents Last 30 Days", "incident", true, null,
                        List.of(new AdHocQueryFilter("status", "eq", "REOPENED")),
                        new AdHocQueryRequest.DateRange(now.minusDays(30), now, null)),
                new T("Incidents by Location", "incident", false, "location", List.of(), null),
                new T("Open Requests by Catalog Item", "service_request", false, "catalogItem",
                        List.of(new AdHocQueryFilter("status", "in", OPEN_REQUEST)), null),
                new T("Open Incidents Older Than 14 Days", "incident", true, null,
                        List.of(new AdHocQueryFilter("status", "in", OPEN_INCIDENT)),
                        new AdHocQueryRequest.DateRange(now.minusMonths(6), now.minusDays(14), null)),
                new T("Incidents by Category", "incident", false, "category", List.of(), null),
                new T("All Requests by Catalog Item", "service_request", false, "catalogItem",
                        List.of(), null),
                new T("Changes Scheduled This Week", "change", true, null,
                        List.of(new AdHocQueryFilter("status", "in", "APPROVED,SCHEDULED,IN_PROGRESS")),
                        new AdHocQueryRequest.DateRange(now, now.plusDays(7), "plannedStart")),
                new T("Changes by Risk", "change", false, "risk", List.of(), null));

        for (T t : templates) {
            AdHocQueryResponse res = assertDoesNotThrow(() -> service.adHocQuery(orgId,
                    new AdHocQueryRequest(t.entity(), t.filters(), t.groupBy(), t.range(),
                            t.detailed() ? Boolean.TRUE : null, null, null), null),
                    "Template failed: " + t.name());
            assertNotNull(res.rows(), t.name());
        }
    }

    /**
     * Reads the REAL AdHocQueryBuilder.tsx + DataExport.tsx constants and
     * asserts every status-filter token parses against the actual enums.
     * Guards the exact class of bug where a template references a status
     * that never existed (e.g. Incident.Status.ASSIGNED) — Java-side mirrors
     * of the TS constants can silently diverge, so this reads the source.
     */
    @Test
    void frontendTemplateStatusValuesMatchRealEnums() throws Exception {
        java.nio.file.Path frontendDir = java.nio.file.Path.of(
                "src/main/frontend/src/pages/dashboard");
        String qb = java.nio.file.Files.readString(frontendDir.resolve("AdHocQueryBuilder.tsx"));
        String de = java.nio.file.Files.readString(frontendDir.resolve("DataExport.tsx"));

        Map<String, Class<? extends Enum<?>>> enumByEntity = Map.of(
                "incident", Incident.Status.class,
                "service_request", ServiceRequest.Status.class,
                "problem", Problem.Status.class,
                "change", ChangeRequest.Status.class);

        // 1. Top-level TSX constants (e.g. OPEN_INCIDENT = 'A,B,C').
        Map<String, String> consts = new HashMap<>();
        var constMatcher = java.util.regex.Pattern
                .compile("const (\\w+) = '([A-Z_,]+)'").matcher(qb);
        while (constMatcher.find()) {
            consts.put(constMatcher.group(1), constMatcher.group(2));
        }
        assertTrue(consts.containsKey("OPEN_INCIDENT"), "OPEN_INCIDENT const not found in TSX");

        // 2. Every 'status' filter inside each builder template. Templates are
        // object literals containing entity + filters with value = const or literal.
        var tplMatcher = java.util.regex.Pattern
                .compile("entity: '(incident|service_request|problem|change)'.*?(?=\\{ name:|\\])",
                        java.util.regex.Pattern.DOTALL)
                .matcher(qb);
        int statusFilters = 0;
        while (tplMatcher.find()) {
            String tpl = tplMatcher.group(0);
            String entity = tpl.substring(tpl.indexOf("'") + 1, tpl.indexOf("'", tpl.indexOf("'") + 1));
            var fMatcher = java.util.regex.Pattern
                    .compile("field: 'status'[^}]*?value: ([A-Za-z_'][^,}]*)").matcher(tpl);
            while (fMatcher.find()) {
                statusFilters++;
                String raw = fMatcher.group(1).trim();
                String resolved;
                if (raw.startsWith("'")) {
                    resolved = raw.replace("'", "");
                } else {
                    resolved = consts.get(raw);
                    if (resolved == null) failValue(raw);
                }
                for (String token : resolved.split(",")) {
                    assertDoesNotThrow(() -> Enum.valueOf(
                            (Class) enumByEntity.get(entity), token.trim()),
                            () -> entity + " status filter references invalid enum value: " + token
                                    + " (template: " + tpl.substring(0, Math.min(80, tpl.length())) + "…)");
                }
            }
        }
        assertTrue(statusFilters >= 8, "expected >=8 status filters parsed from templates, got " + statusFilters);

        // 3. DataExport ENTITIES status lists (these drive the export filter
        // that hits the same enum parse path server-side).
        var entMatcher = java.util.regex.Pattern
                .compile("value: '(incident|service_request|problem|change)'.*?statuses: \\[([^]]+)]",
                        java.util.regex.Pattern.DOTALL)
                .matcher(de);
        int exportLists = 0;
        while (entMatcher.find()) {
            exportLists++;
            Class<? extends Enum<?>> enumClass = enumByEntity.get(entMatcher.group(1));
            for (String tok : entMatcher.group(2).split(",")) {
                String v = tok.trim().replace("'", "");
                if (v.isEmpty()) continue;
                assertDoesNotThrow(() -> Enum.valueOf((Class) enumClass, v),
                        () -> "DataExport " + entMatcher.group(1) + " lists invalid status: " + v);
            }
        }
        assertEquals(4, exportLists, "expected 4 entity status lists in DataExport");
    }

    private static String failValue(String raw) {
        throw new AssertionError("template references unknown const: " + raw);
    }

    /** Fully-stubbed criteria engine: any entity root, per-field java types,
     * empty result lists. Mirrors adHocQuery's grouped + detailed plumbing. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubAdHocEngine() {
        lenient().when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        lenient().when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
        lenient().when(criteriaQuery.from(any(Class.class))).thenAnswer(inv -> {
            Root root = mock(Root.class);
            stubRoot(root, inv.getArgument(0));
            return root;
        });
        lenient().when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        lenient().when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
        lenient().when(criteriaQuery.groupBy(any(Expression[].class))).thenReturn(criteriaQuery);
        lenient().when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        lenient().when(typedQuery.setMaxResults(anyInt())).thenReturn(typedQuery);
        lenient().when(typedQuery.getResultList()).thenReturn(List.of());

        // detailed mode: count query + entity select query
        CriteriaQuery<Long> countQ = mock(CriteriaQuery.class);
        CriteriaQuery<Object> selectQ = mock(CriteriaQuery.class);
        TypedQuery<Long> countTyped = mock(TypedQuery.class);
        TypedQuery<Object> selectTyped = mock(TypedQuery.class);
        lenient().when(criteriaBuilder.createQuery(Long.class)).thenReturn(countQ);
        lenient().when(criteriaBuilder.createQuery(Object.class)).thenReturn(selectQ);
        lenient().when(countQ.from(any(Class.class))).thenAnswer(inv -> {
            Root r = mock(Root.class);
            stubRoot(r, inv.getArgument(0));
            return r;
        });
        lenient().when(selectQ.from(any(Class.class))).thenAnswer(inv -> {
            Root r = mock(Root.class);
            stubRoot(r, inv.getArgument(0));
            return r;
        });
        lenient().when(criteriaBuilder.count(any())).thenReturn(countExpr);
        lenient().when(countQ.select(any(Selection.class))).thenReturn(countQ);
        lenient().when(countQ.where(any(Predicate[].class))).thenReturn(countQ);
        lenient().when(selectQ.select(any(Selection.class))).thenReturn(selectQ);
        lenient().when(selectQ.where(any(Predicate[].class))).thenReturn(selectQ);
        lenient().when(selectQ.orderBy(any(Order.class))).thenReturn(selectQ);
        lenient().when(criteriaBuilder.desc(any())).thenReturn(mock(Order.class));
        lenient().when(entityManager.createQuery(countQ)).thenReturn(countTyped);
        lenient().when(countTyped.getSingleResult()).thenReturn(0L);
        lenient().when(entityManager.createQuery(selectQ)).thenReturn(selectTyped);
        lenient().when(selectTyped.setFirstResult(anyInt())).thenReturn(selectTyped);
        lenient().when(selectTyped.setMaxResults(anyInt())).thenReturn(selectTyped);
        lenient().when(selectTyped.getResultList()).thenReturn(List.of());

        // predicate combinators
        lenient().when(criteriaBuilder.equal(any(), any())).thenReturn(predicate);
        lenient().when(criteriaBuilder.notEqual(any(), any())).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNull(any())).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNotNull(any())).thenReturn(predicate);
        lenient().when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.or(any(Predicate[].class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.not(any())).thenReturn(predicate);
        lenient().when(criteriaBuilder.between(any(Expression.class), any(Expression.class), any(Expression.class)))
                .thenReturn(predicate);
        lenient().when(criteriaBuilder.between(any(Expression.class), any(Comparable.class), any(Comparable.class)))
                .thenReturn(predicate);
        lenient().when(criteriaBuilder.lower(any())).thenReturn(mock(Expression.class));
        lenient().when(criteriaBuilder.coalesce(any(), any())).thenReturn((Expression) path);
        lenient().when(criteriaBuilder.literal(any())).thenReturn(path);
        lenient().when(entityManager.getReference(any(Class.class), any()))
                .thenAnswer(inv -> mock((Class) inv.getArgument(0)));
    }

    /** Per-field path stubs: correct javaType per entity field so enum/entity
     * parsing takes the right branch. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubRoot(Root<?> root, Class<?> entity) {
        lenient().when(root.get(anyString())).thenAnswer(inv -> {
            String field = inv.getArgument(0);
            Path p = mock(Path.class);
            lenient().when(p.getJavaType()).thenReturn((Class) fieldClass(entity, field));
            lenient().when(p.get(anyString())).thenReturn(p);
            lenient().when(p.as(any())).thenReturn(p);
            lenient().when(p.in(any(java.util.Collection.class))).thenReturn(predicate);
            return p;
        });
        Join join = mock(Join.class);
        lenient().when(join.get(anyString())).thenAnswer(inv -> {
            Path p = mock(Path.class);
            lenient().when(p.getJavaType()).thenReturn((Class) Object.class);
            lenient().when(p.as(any())).thenReturn(p);
            return p;
        });
        lenient().when(root.join(anyString(), any(JoinType.class))).thenReturn(join);
    }

    private Class<?> fieldClass(Class<?> entity, String field) {
        if (entity == Incident.class) {
            return switch (field) {
                case "status" -> Incident.Status.class;
                case "priority" -> com.alignedcardio.itsm.entity.Priority.class;
                case "category" -> com.alignedcardio.itsm.entity.Category.class;
                case "assignee", "requester" -> AppUser.class;
                case "location" -> Location.class;
                case "orgId" -> UUID.class;
                case "createdAt", "resolvedAt", "closedAt" -> OffsetDateTime.class;
                case "impact", "urgency" -> int.class;
                default -> String.class;
            };
        }
        if (entity == ServiceRequest.class) {
            return switch (field) {
                case "status" -> ServiceRequest.Status.class;
                case "requester", "approver" -> AppUser.class;
                case "catalogItem" -> CatalogItem.class;
                case "location" -> Location.class;
                case "orgId" -> UUID.class;
                case "createdAt", "decidedAt" -> OffsetDateTime.class;
                default -> String.class;
            };
        }
        if (entity == ChangeRequest.class) {
            return switch (field) {
                case "status" -> ChangeRequest.Status.class;
                case "changeType" -> ChangeRequest.ChangeType.class;
                case "risk" -> ChangeRequest.Risk.class;
                case "assignee", "requestedBy" -> AppUser.class;
                case "orgId" -> UUID.class;
                case "createdAt", "plannedStart" -> OffsetDateTime.class;
                default -> String.class;
            };
        }
        if (entity == Problem.class) {
            return switch (field) {
                case "status" -> Problem.Status.class;
                case "assignee" -> AppUser.class;
                case "orgId" -> UUID.class;
                case "createdAt", "resolvedAt", "closedAt" -> OffsetDateTime.class;
                default -> String.class;
            };
        }
        return String.class;
    }

    // --- Part A regression: "in" operator on enum fields ------------------

    /**
     * A comma-separated status list must be SPLIT before enum parsing —
     * previously parseValue() ran eagerly on the whole "NEW,IN_PROGRESS"
     * string, throwing "No enum constant" before the in-branch could split it.
     */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void inOperatorSplitsCommaSeparatedEnumList_incident() {
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
        when(criteriaQuery.from(Incident.class)).thenReturn(root);
        when(root.get(anyString())).thenReturn(path);
        when(path.getJavaType()).thenReturn((Class) Incident.Status.class);
        when(criteriaBuilder.count(root)).thenReturn(countExpr);
        lenient().when(criteriaBuilder.equal(any(Path.class), any(Object.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNull(any(Path.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(tuple));
        when(tuple.get(0)).thenReturn(9L);

        ReportingService service = new ReportingService(entityManager);
        // Would previously throw IllegalArgumentException "No enum constant
        // Incident.Status.NEW,IN_PROGRESS,ON_HOLD".
        service.adHocQuery(UUID.randomUUID(), new AdHocQueryRequest(
                "incident",
                List.of(new AdHocQueryFilter("status", "in", "NEW,IN_PROGRESS,ON_HOLD")),
                null, null, null, null, null), null);

        ArgumentCaptor<java.util.Collection<?>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(path).in(captor.capture());
        assertEquals(List.of(Incident.Status.NEW, Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD),
                List.copyOf(captor.getValue()));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void inOperatorSplitsCommaSeparatedEnumList_serviceRequest() {
        Root<ServiceRequest> srRoot = mock(Root.class);
        when(entityManager.getCriteriaBuilder()).thenReturn(criteriaBuilder);
        when(criteriaBuilder.createTupleQuery()).thenReturn(criteriaQuery);
        when(criteriaQuery.from(ServiceRequest.class)).thenReturn(srRoot);
        when(srRoot.get(anyString())).thenReturn((Path) path);
        when(path.getJavaType()).thenReturn((Class) ServiceRequest.Status.class);
        when(criteriaBuilder.count(srRoot)).thenReturn(countExpr);
        lenient().when(criteriaBuilder.equal(any(Path.class), any(Object.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.isNull(any(Path.class))).thenReturn(predicate);
        lenient().when(criteriaBuilder.and(any(Predicate[].class))).thenReturn(predicate);
        when(criteriaQuery.where(any(Predicate[].class))).thenReturn(criteriaQuery);
        when(criteriaQuery.multiselect(any(Selection[].class))).thenReturn(criteriaQuery);
        when(entityManager.createQuery(criteriaQuery)).thenReturn(typedQuery);
        when(typedQuery.getResultList()).thenReturn(List.of(tuple));
        when(tuple.get(0)).thenReturn(4L);

        ReportingService service = new ReportingService(entityManager);
        service.adHocQuery(UUID.randomUUID(), new AdHocQueryRequest(
                "service_request",
                List.of(new AdHocQueryFilter("status", "in", "PENDING_APPROVAL,IN_FULFILLMENT")),
                null, null, null, null, null), null);

        ArgumentCaptor<java.util.Collection<?>> captor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(path).in(captor.capture());
        assertEquals(List.of(ServiceRequest.Status.PENDING_APPROVAL, ServiceRequest.Status.IN_FULFILLMENT),
                List.copyOf(captor.getValue()));
    }

    // --- Dashboard consolidation additions -------------------------------

    /** Stubs a JPQL createQuery call matched by substring + result class. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> TypedQuery<T> stubJpql(EntityManager em, String jpqlPart, Class<T> cls,
                                     Object single, List<?> list) {
        TypedQuery<T> q = mock(TypedQuery.class);
        lenient().when(em.createQuery(contains(jpqlPart), eq(cls))).thenReturn(q);
        lenient().when(q.setParameter(anyString(), any())).thenReturn(q);
        lenient().when(q.setMaxResults(anyInt())).thenReturn(q);
        lenient().when(q.getSingleResult()).thenReturn((T) single);
        lenient().when(q.getResultList()).thenReturn((List<T>) list);
        return q;
    }

    /** Config health flags an empty tier team, a manager-less location, an
     * approval-gated catalog item with no fallback approver, and a tierless
     * SLA policy — the exact silent-misconfiguration class it exists for. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void configHealthFlagsBrokenSetup() throws Exception {
        EntityManager em = mock(EntityManager.class);
        UUID orgId = UUID.randomUUID();

        Team team = new Team();
        team.setId(com.alignedcardio.itsm.service.SupportTiers.L2_ID);
        team.setName("L2 Support");
        stubJpql(em, "FROM Team t WHERE t.id", Team.class, null, List.of(team));
        stubJpql(em, "FROM TeamMember m", Long.class, 0L, List.of());
        stubJpql(em, "FROM Location l", String.class, null, List.of("HQ"));

        com.fasterxml.jackson.databind.JsonNode schema = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[{\"name\":\"access\",\"type\":\"select_with_other\",\"options\":"
                        + "[{\"value\":\"EHR\",\"label\":\"EHR\",\"requiresApproval\":true}]}]");
        CatalogItem item = new CatalogItem();
        item.setName("EHR Access");
        item.setActive(true);
        item.setFormSchema(schema);
        stubJpql(em, "FROM CatalogItem c", CatalogItem.class, null, List.of(item));

        stubJpql(em, "FROM SlaPolicy p", String.class, null, List.of("Critical Policy"));

        ReportingService service = new ReportingService(em);
        Map<String, Object> health = service.configHealth(orgId);

        assertEquals(4, ((List<?>) health.get("emptyTeams")).size()); // all four teams empty
        assertEquals(List.of("HQ"), health.get("locationsWithoutApprover"));
        assertEquals(List.of("EHR Access"), health.get("itemsNeedingApprover"));
        assertEquals(List.of("Critical Policy"), health.get("policiesWithoutTiers"));
    }

    /** Clean config → every list empty (the frontend renders the green
     * all-clear state from this). */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void configHealthAllClearWhenNothingBroken() {
        EntityManager em = mock(EntityManager.class);

        Team team = new Team();
        team.setId(UUID.randomUUID());
        team.setName("L1 Support");
        stubJpql(em, "FROM Team t WHERE t.id", Team.class, null, List.of(team));
        stubJpql(em, "FROM TeamMember m", Long.class, 3L, List.of());
        stubJpql(em, "FROM Location l", String.class, null, List.of());
        stubJpql(em, "FROM CatalogItem c", CatalogItem.class, null, List.of());
        stubJpql(em, "FROM SlaPolicy p", String.class, null, List.of());

        ReportingService service = new ReportingService(em);
        Map<String, Object> health = service.configHealth(UUID.randomUUID());

        assertEquals(List.of(), health.get("emptyTeams"));
        assertEquals(List.of(), health.get("locationsWithoutApprover"));
        assertEquals(List.of(), health.get("itemsNeedingApprover"));
        assertEquals(List.of(), health.get("policiesWithoutTiers"));
    }

    /** Overdue deliveries: a task past expectedDeliveryDate with deliveredAt
     * null surfaces with count + oldest; a delivered task does not. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void serviceRequestOpsReportsOverdueDeliveries() {
        EntityManager em = mock(EntityManager.class);

        stubJpql(em, "GROUP BY s.status", Tuple.class, null, List.of());
        stubJpql(em, "SELECT s FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL "
                + "AND s.status NOT IN :terminal", com.alignedcardio.itsm.entity.ServiceRequest.class,
                null, List.of());
        stubJpql(em, "ft.assignee IS NULL", Long.class, 0L, List.of());
        stubJpql(em, "SELECT s.status, s.createdAt", Tuple.class, null, List.of());
        stubJpql(em, "COUNT(si) FROM SlaInstance", Long.class, 0L, List.of());
        stubJpql(em, "a.displayName", Tuple.class, null, List.of());

        com.alignedcardio.itsm.entity.ServiceRequest sr =
                new com.alignedcardio.itsm.entity.ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setNumber("SR-9");
        FulfillmentTask overdue = new FulfillmentTask();
        overdue.setId(UUID.randomUUID());
        overdue.setServiceRequest(sr);
        overdue.setDescription("Ship replacement laptop");
        overdue.setExpectedDeliveryDate(java.time.LocalDate.now().minusDays(3));
        overdue.setStatus(FulfillmentTask.Status.PENDING);
        stubJpql(em, "expectedDeliveryDate < :today", FulfillmentTask.class, null, List.of(overdue));

        ReportingService service = new ReportingService(em);
        Map<String, Object> ops = service.serviceRequestOps(UUID.randomUUID());

        Map<String, Object> deliveries = (Map<String, Object>) ops.get("overdueDeliveries");
        assertEquals(1, deliveries.get("count"));
        Map<String, Object> oldest = (Map<String, Object>) deliveries.get("oldest");
        assertEquals("SR-9", oldest.get("requestNumber"));
        assertEquals("Ship replacement laptop", oldest.get("description"));
    }

    /** Needs-attention payload now carries KB review + unassigned-task counts. */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void needsAttentionIncludesKbAndUnassignedTasks() {
        EntityManager em = mock(EntityManager.class);

        stubJpql(em, "FROM Incident i", Incident.class, null, List.of());
        stubJpql(em, "FROM SlaInstance si", Tuple.class, null, List.of());
        stubJpql(em, "FROM ServiceRequest s", com.alignedcardio.itsm.entity.ServiceRequest.class,
                null, List.of());
        stubJpql(em, "COUNT(s) FROM ServiceRequest", Long.class, 0L, List.of());
        stubJpql(em, "FROM KbArticle a", Long.class, 3L, List.of());
        stubJpql(em, "FROM FulfillmentTask ft", Long.class, 2L, List.of());

        ReportingService service = new ReportingService(em);
        Map<String, Object> out = service.needsAttention(UUID.randomUUID());

        assertEquals(3L, out.get("kbPendingReview"));
        assertEquals(2L, out.get("unassignedFulfillmentTasks"));
    }
}
