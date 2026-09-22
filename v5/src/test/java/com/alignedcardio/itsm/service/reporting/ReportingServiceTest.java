package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Team;
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
                UUID.randomUUID(), agentId, from, to, null, null);

        assertEquals(1, rows.size());
        Map<String, Object> row = rows.get(0);
        assertEquals("INCIDENT", row.get("type"));
        assertEquals("INC-9", row.get("number"));
        assertEquals(workedAt, row.get("workedAt"));
        // The audit query — not a createdAt filter — decided inclusion.
        verify(auditQuery).setParameter("from", from);
        verify(auditQuery).setParameter("agent", agentId);
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
