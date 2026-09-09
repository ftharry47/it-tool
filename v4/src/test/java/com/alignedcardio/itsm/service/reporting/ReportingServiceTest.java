package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.Incident;
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
                () -> service.adHocQuery(UUID.randomUUID(), request));

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
                () -> service.adHocQuery(UUID.randomUUID(), request));

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
                () -> service.adHocQuery(UUID.randomUUID(), request));

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

        AdHocQueryResponse response = service.adHocQuery(orgId, request);

        assertEquals(orgId, response.orgId());
        assertEquals("incident", response.entity());
        assertEquals("status", response.groupBy());
        assertEquals(1, response.rows().size());

        Map<String, Object> row = response.rows().get(0);
        assertEquals(Incident.Status.NEW.name(), row.get("group"));
        assertEquals(5L, row.get("count"));

        verify(criteriaBuilder).equal(path, orgId);
        verify(criteriaBuilder).equal(path, Incident.Status.NEW);
        verify(criteriaQuery).groupBy(any(Expression[].class));
        verify(typedQuery).setMaxResults(1000);
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
