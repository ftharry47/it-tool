package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.sla.SlaInstanceDetailResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression: soft-deleted incidents/service requests must never surface in
 * the SLA detail list — neither in the admin view nor in "My SLA".
 */
@ExtendWith(MockitoExtension.class)
class SlaDetailsServiceTest {

    private static final UUID ORG = BaseEntity.DEFAULT_ORG_ID;

    @Mock private FulfillmentTaskRepository fulfillmentTaskRepository;

    private EntityManager entityManager;
    private SlaDetailsService service;
    private TypedQuery<SlaInstance> query;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class, RETURNS_DEEP_STUBS);
        query = mock(TypedQuery.class);
        when(entityManager.createQuery(any(CriteriaQuery.class))).thenReturn(query);
        when(entityManager.createEntityGraph(SlaInstance.class))
                .thenReturn((EntityGraph) mock(EntityGraph.class, RETURNS_DEEP_STUBS));
        when(query.setHint(anyString(), any())).thenReturn(query);
        service = new SlaDetailsService(entityManager, fulfillmentTaskRepository);
    }

    @Test
    void listExcludesDeletedLinkedTickets() {
        AppUser agent = user();
        Incident deletedIncident = incident("Deleted incident");
        deletedIncident.setDeletedAt(OffsetDateTime.now());
        Incident activeIncident = incident("Active incident");
        ServiceRequest deletedRequest = serviceRequest("SR-1");
        deletedRequest.setDeletedAt(OffsetDateTime.now());

        when(query.getResultList()).thenReturn(List.of(
                sla(deletedIncident, null),
                sla(activeIncident, null),
                sla(null, deletedRequest),
                sla(null, serviceRequest("SR-2"))));

        List<SlaInstanceDetailResponse> rows =
                service.list(ORG, null, null, null, null, agent, false);

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(r -> "Active incident".equals(r.incidentTitle())));
        assertTrue(rows.stream().anyMatch(r -> "SR-2".equals(r.serviceRequestNumber())));
        assertFalse(rows.stream().anyMatch(r -> "Deleted incident".equals(r.incidentTitle())));
        assertFalse(rows.stream().anyMatch(r -> "SR-1".equals(r.serviceRequestNumber())));
    }

    @Test
    void mySlaListAlsoExcludesDeletedLinkedTickets() {
        AppUser agent = user();
        Incident deletedIncident = incident("Deleted incident");
        deletedIncident.setDeletedAt(OffsetDateTime.now());
        Incident activeIncident = incident("Active incident");

        FulfillmentTask task = new FulfillmentTask();
        task.setServiceRequest(serviceRequest("SR-2"));
        when(fulfillmentTaskRepository.findByAssignee_IdAndDeletedAtIsNull(agent.getId()))
                .thenReturn(List.of(task));

        when(query.getResultList()).thenReturn(List.of(
                sla(deletedIncident, null),
                sla(activeIncident, null)));

        List<SlaInstanceDetailResponse> rows =
                service.list(ORG, null, null, null, null, agent, true);

        assertEquals(1, rows.size());
        assertEquals("Active incident", rows.get(0).incidentTitle());
    }

    /**
     * Regression: the linked-ticket filter must use LEFT joins. The previous
     * implicit-inner-join dereferences silently dropped every SLA instance whose
     * incident FK was null — i.e. all service-request-backed rows.
     */
    @Test
    void listUsesLeftJoinsSoServiceRequestBackedRowsSurvive() {
        AppUser agent = user();
        when(query.getResultList()).thenReturn(List.of(sla(null, serviceRequest("SR-9"))));

        List<SlaInstanceDetailResponse> rows =
                service.list(ORG, null, null, null, null, agent, false);

        assertEquals(1, rows.size());
        assertEquals("SR-9", rows.get(0).serviceRequestNumber());

        Root<SlaInstance> root = entityManager.getCriteriaBuilder()
                .createQuery(SlaInstance.class).from(SlaInstance.class);
        verify(root).join("incident", JoinType.LEFT);
        verify(root).join("serviceRequest", JoinType.LEFT);
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(UUID.randomUUID());
        user.setOrgId(ORG);
        return user;
    }

    private Incident incident(String title) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setOrgId(ORG);
        incident.setTitle(title);
        incident.setNumber(1L);
        return incident;
    }

    private ServiceRequest serviceRequest(String number) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(ORG);
        sr.setNumber(number);
        return sr;
    }

    private SlaInstance sla(Incident incident, ServiceRequest sr) {
        SlaPolicy policy = new SlaPolicy();
        policy.setName("SLA");
        SlaInstance si = new SlaInstance();
        si.setId(UUID.randomUUID());
        si.setOrgId(ORG);
        si.setPolicy(policy);
        si.setIncident(incident);
        si.setServiceRequest(sr);
        si.setBreachStatus(SlaInstance.BreachStatus.ON_TRACK);
        return si;
    }
}
