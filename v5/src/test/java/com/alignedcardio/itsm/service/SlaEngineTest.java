package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.repository.BusinessCalendarRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.SlaPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaEngineTest {

    @Mock
    private SlaPolicyRepository slaPolicyRepository;

    @Mock
    private SlaInstanceRepository slaInstanceRepository;

    @Mock
    private BusinessCalendarRepository businessCalendarRepository;

    private BusinessHoursCalculator businessHoursCalculator;
    private SlaEngine slaEngine;

    @BeforeEach
    void setup() {
        businessHoursCalculator = new BusinessHoursCalculator();
        slaEngine = new SlaEngine(slaPolicyRepository, slaInstanceRepository, businessCalendarRepository, businessHoursCalculator);
    }

    @Test
    void onHoldSetsPausedAt() {
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.ON_HOLD);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        verify(slaInstanceRepository).save(any(SlaInstance.class));
        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        assertNotNull(captor.getValue().getPausedAt());
    }

    @Test
    void leavingOnHoldExtendsDueDatesAndAccumulatesPausedMinutes() {
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T12:00:00Z");
        SlaInstance instance = createInstance();
        instance.setCreatedAt(start);
        instance.setResponseDueAt(start.plusMinutes(60));
        instance.setResolutionDueAt(start.plusMinutes(240));
        instance.setPausedAt(OffsetDateTime.now().minusMinutes(30));

        // 24/7 calendar so the math is straightforward
        BusinessCalendar calendar = create24x7Calendar();
        SlaPolicy policy = new SlaPolicy();
        policy.setResponseTargetMinutes(60);
        policy.setResolutionTargetMinutes(240);
        policy.setBusinessHoursCalendar(calendar);
        instance.setPolicy(policy);

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.IN_PROGRESS);
        incident.setCreatedAt(start);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        verify(slaInstanceRepository).save(any(SlaInstance.class));
        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        SlaInstance saved = captor.getValue();
        assertNull(saved.getPausedAt());
        assertTrue(saved.getTotalPausedMinutes() >= 29 && saved.getTotalPausedMinutes() <= 31,
                "Expected ~30 paused minutes, got " + saved.getTotalPausedMinutes());
        assertNotNull(saved.getResponseMetAt(),
                "Resuming to IN_PROGRESS marks the response clock met");
        assertEquals(start.plusMinutes(60), saved.getResponseDueAt(),
                "Met response clocks keep their original due date");
        assertTrue(saved.getResolutionDueAt().isAfter(start.plusMinutes(240)),
                "Resolution due date should be extended after a pause");
    }

    @Test
    void waitingOnCustomerSetsPausedAt() {
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.WAITING_ON_CUSTOMER);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        verify(slaInstanceRepository).save(any(SlaInstance.class));
        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        assertNotNull(captor.getValue().getPausedAt());
    }

    @Test
    void leavingWaitingOnCustomerExtendsDueDatesAndAccumulatesPausedMinutes() {
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T12:00:00Z");
        SlaInstance instance = createInstance();
        instance.setCreatedAt(start);
        instance.setResponseDueAt(start.plusMinutes(60));
        instance.setResolutionDueAt(start.plusMinutes(240));
        instance.setPausedAt(OffsetDateTime.now().minusMinutes(30));

        // 24/7 calendar so the math is straightforward
        BusinessCalendar calendar = create24x7Calendar();
        SlaPolicy policy = new SlaPolicy();
        policy.setResponseTargetMinutes(60);
        policy.setResolutionTargetMinutes(240);
        policy.setBusinessHoursCalendar(calendar);
        instance.setPolicy(policy);

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.IN_PROGRESS);
        incident.setCreatedAt(start);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        verify(slaInstanceRepository).save(any(SlaInstance.class));
        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        SlaInstance saved = captor.getValue();
        assertNull(saved.getPausedAt());
        assertTrue(saved.getTotalPausedMinutes() >= 29 && saved.getTotalPausedMinutes() <= 31,
                "Expected ~30 paused minutes, got " + saved.getTotalPausedMinutes());
        assertNotNull(saved.getResponseMetAt(),
                "Resuming to IN_PROGRESS marks the response clock met");
        assertEquals(start.plusMinutes(60), saved.getResponseDueAt(),
                "Met response clocks keep their original due date");
        assertTrue(saved.getResolutionDueAt().isAfter(start.plusMinutes(240)),
                "Resolution due date should be extended after a pause");
    }

    @Test
    void resolvingTicketSetsResolutionMetAt() {
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.RESOLVED);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        assertNotNull(captor.getValue().getResolutionMetAt());
    }

    @Test
    void nonNewStatusMarksFirstResponse() {
        // Any engagement status counts as the first response — not just a
        // public comment (mirrors SR/problem/change response-status sets).
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.IN_PROGRESS);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertNotNull(captor.getValue().getResponseMetAt(),
                "IN_PROGRESS must mark the response clock as met");
        assertNull(captor.getValue().getResolutionMetAt());
    }

    @Test
    void closedWithoutAnyCommentStopsBothClocks() {
        // Regression: NEW -> IN_PROGRESS -> RESOLVED -> CLOSED handled purely
        // through status changes (no public comment) must stop BOTH clocks.
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.CLOSED);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        SlaInstance saved = captor.getValue();
        assertNotNull(saved.getResponseMetAt(), "CLOSED must mark the response clock as met");
        assertNotNull(saved.getResolutionMetAt(), "CLOSED must mark the resolution clock as met");
        assertEquals(SlaInstance.BreachStatus.ON_TRACK, saved.getBreachStatus(),
                "A closed ticket's SLA must be terminal, not still calculating");
    }

    @Test
    void newStatusDoesNotMarkFirstResponse() {
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setStatus(Incident.Status.NEW);

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.onStatusChanged(incident);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertNull(captor.getValue().getResponseMetAt());
    }

    @Test
    void firstPublicCommentRecordsResponseMetAt() {
        SlaInstance instance = createInstance();

        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());

        OffsetDateTime respondedAt = OffsetDateTime.now();

        when(slaInstanceRepository.findByIncidentId(incident.getId())).thenReturn(Optional.of(instance));

        slaEngine.recordFirstResponse(incident, respondedAt);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());

        assertEquals(respondedAt, captor.getValue().getResponseMetAt());
    }

    // --- Service request SLA (parity with the incident responseMetAt fix) ---

    private ServiceRequest serviceRequest(ServiceRequest.Status status, String... taskWorkflows) {
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(UUID.randomUUID());
        sr.setStatus(status);
        sr.setCreatedAt(OffsetDateTime.now().minusHours(1));
        CatalogItem item = new CatalogItem();
        StringBuilder tasks = new StringBuilder("[");
        for (int i = 0; i < taskWorkflows.length; i++) {
            if (i > 0) tasks.append(',');
            tasks.append("{\"description\":\"t\",\"workflow\":\"").append(taskWorkflows[i]).append("\"}");
        }
        tasks.append("]");
        try {
            item.setFulfillmentTasks(new com.fasterxml.jackson.databind.ObjectMapper().readTree(tasks.toString()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        sr.setCatalogItem(item);
        return sr;
    }

    private SlaInstance srInstance(ServiceRequest sr) {
        SlaInstance instance = createInstance();
        when(slaInstanceRepository.findByServiceRequest_Id(sr.getId())).thenReturn(Optional.of(instance));
        return instance;
    }

    @Test
    void rejectedServiceRequestStopsBothClocks() {
        // Regression: PENDING_APPROVAL -> REJECTED without a fulfilled response
        // must stop BOTH clocks — previously responseMetAt stayed null and the
        // UI showed a live countdown on a dead request.
        ServiceRequest sr = serviceRequest(ServiceRequest.Status.REJECTED);
        SlaInstance instance = srInstance(sr);

        slaEngine.onServiceRequestStatusChanged(sr);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertNotNull(captor.getValue().getResponseMetAt());
        assertNotNull(captor.getValue().getResolutionMetAt());
    }

    @Test
    void cancelledServiceRequestStopsBothClocks() {
        ServiceRequest sr = serviceRequest(ServiceRequest.Status.CANCELLED);
        SlaInstance instance = srInstance(sr);

        slaEngine.onServiceRequestStatusChanged(sr);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertNotNull(captor.getValue().getResponseMetAt());
        assertNotNull(captor.getValue().getResolutionMetAt());
        assertEquals(SlaInstance.BreachStatus.ON_TRACK, captor.getValue().getBreachStatus());
    }

    @Test
    void pendingApprovalPausesWithoutMarkingResponse() {
        ServiceRequest sr = serviceRequest(ServiceRequest.Status.PENDING_APPROVAL);
        SlaInstance instance = srInstance(sr);

        slaEngine.onServiceRequestStatusChanged(sr);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertNotNull(captor.getValue().getPausedAt());
        assertNull(captor.getValue().getResponseMetAt(),
                "Approval routing alone is not a response");
    }

    @Test
    void instantWorkflowPrefersWorkflowSpecificPolicy() {
        // A REQUEST policy scoped to INSTANT must win over a generic one.
        BusinessCalendar calendar = create24x7Calendar();
        SlaPolicy generic = new SlaPolicy();
        generic.setBusinessHoursCalendar(calendar);
        generic.setResponseTargetMinutes(240);
        generic.setResolutionTargetMinutes(2880);
        SlaPolicy instant = new SlaPolicy();
        instant.setBusinessHoursCalendar(calendar);
        instant.setWorkflowType("INSTANT");
        instant.setResponseTargetMinutes(15);
        instant.setResolutionTargetMinutes(60);

        ServiceRequest sr = serviceRequest(ServiceRequest.Status.SUBMITTED, "INSTANT");
        when(slaPolicyRepository.findByOrgIdAndAppliesTo(sr.getOrgId(), SlaPolicy.AppliesTo.REQUEST))
                .thenReturn(java.util.List.of(generic, instant));

        assertTrue(slaEngine.onServiceRequestCreated(sr));

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertSame(instant, captor.getValue().getPolicy(),
                "INSTANT request must match the INSTANT-scoped policy");
    }

    @Test
    void fullWorkflowFallsBackToGenericPolicy() {
        // A FULL request must NOT match an INSTANT-scoped policy.
        BusinessCalendar calendar = create24x7Calendar();
        SlaPolicy generic = new SlaPolicy();
        generic.setBusinessHoursCalendar(calendar);
        generic.setResponseTargetMinutes(240);
        generic.setResolutionTargetMinutes(2880);
        SlaPolicy instant = new SlaPolicy();
        instant.setBusinessHoursCalendar(calendar);
        instant.setWorkflowType("INSTANT");
        instant.setResponseTargetMinutes(15);
        instant.setResolutionTargetMinutes(60);

        ServiceRequest sr = serviceRequest(ServiceRequest.Status.SUBMITTED, "FULL");
        when(slaPolicyRepository.findByOrgIdAndAppliesTo(sr.getOrgId(), SlaPolicy.AppliesTo.REQUEST))
                .thenReturn(java.util.List.of(generic, instant));

        assertTrue(slaEngine.onServiceRequestCreated(sr));

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertSame(generic, captor.getValue().getPolicy());
    }

    @Test
    void mixedTasksResolveToHeaviestWorkflow() {
        // INSTANT + FULL tasks -> effective workflow FULL -> generic/FULL policy.
        BusinessCalendar calendar = create24x7Calendar();
        SlaPolicy instant = new SlaPolicy();
        instant.setBusinessHoursCalendar(calendar);
        instant.setWorkflowType("INSTANT");
        SlaPolicy generic = new SlaPolicy();
        generic.setBusinessHoursCalendar(calendar);

        ServiceRequest sr = serviceRequest(ServiceRequest.Status.SUBMITTED, "INSTANT", "FULL");
        when(slaPolicyRepository.findByOrgIdAndAppliesTo(sr.getOrgId(), SlaPolicy.AppliesTo.REQUEST))
                .thenReturn(java.util.List.of(generic, instant));

        assertTrue(slaEngine.onServiceRequestCreated(sr));

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        assertSame(generic, captor.getValue().getPolicy());
    }

    @Test
    void serviceRequestPriorityChangeRematchesPolicyAndRecomputesDueDates() {
        // Regression: changing priority must re-match the policy and re-target
        // unmet clocks — previously Edit changed the field but SLA stayed put.
        OffsetDateTime start = OffsetDateTime.parse("2026-01-01T12:00:00Z");
        BusinessCalendar calendar = create24x7Calendar();

        SlaPolicy newPolicy = new SlaPolicy();
        newPolicy.setBusinessHoursCalendar(calendar);
        newPolicy.setResponseTargetMinutes(30);
        newPolicy.setResolutionTargetMinutes(120);

        SlaInstance instance = createInstance();
        instance.setCreatedAt(start);
        instance.setResponseDueAt(start.plusMinutes(240));
        instance.setResolutionDueAt(start.plusMinutes(2880));

        ServiceRequest sr = serviceRequest(ServiceRequest.Status.IN_FULFILLMENT);
        sr.setCreatedAt(start);
        when(slaInstanceRepository.findByServiceRequest_Id(sr.getId())).thenReturn(Optional.of(instance));
        when(slaPolicyRepository.findByOrgIdAndAppliesTo(sr.getOrgId(), SlaPolicy.AppliesTo.REQUEST))
                .thenReturn(java.util.List.of(newPolicy));

        slaEngine.onServiceRequestPriorityChanged(sr);

        ArgumentCaptor<SlaInstance> captor = ArgumentCaptor.forClass(SlaInstance.class);
        verify(slaInstanceRepository).save(captor.capture());
        SlaInstance saved = captor.getValue();
        assertSame(newPolicy, saved.getPolicy());
        assertEquals(start.plusMinutes(30), saved.getResponseDueAt(),
                "Unmet response clock must re-target on priority change");
        assertEquals(start.plusMinutes(120), saved.getResolutionDueAt());
    }

    private SlaInstance createInstance() {
        SlaInstance instance = new SlaInstance();
        instance.setPolicy(new SlaPolicy());
        return instance;
    }

    private BusinessCalendar create24x7Calendar() {
        BusinessCalendar calendar = new BusinessCalendar();
        calendar.setTimezone("UTC");
        calendar.setWorkingHours("""
                {
                  "monday": {"start": "00:00", "end": "23:59"},
                  "tuesday": {"start": "00:00", "end": "23:59"},
                  "wednesday": {"start": "00:00", "end": "23:59"},
                  "thursday": {"start": "00:00", "end": "23:59"},
                  "friday": {"start": "00:00", "end": "23:59"},
                  "saturday": {"start": "00:00", "end": "23:59"},
                  "sunday": {"start": "00:00", "end": "23:59"}
                }
                """);
        calendar.setHolidays("[]");
        return calendar;
    }
}
