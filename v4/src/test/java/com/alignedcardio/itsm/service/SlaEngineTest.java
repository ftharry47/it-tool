package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.Incident;
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
        assertTrue(saved.getResponseDueAt().isAfter(start.plusMinutes(60)),
                "Response due date should be extended after a pause");
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
