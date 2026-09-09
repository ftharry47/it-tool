package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.event.ServiceRequestEvent;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FulfillmentReminderJobTest {

    private static final UUID ORG_ID = UUID.randomUUID();
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    @Mock private FulfillmentTaskRepository fulfillmentTaskRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private FulfillmentReminderJob job;

    @BeforeEach
    void setUp() {
        job = new FulfillmentReminderJob(fulfillmentTaskRepository, eventPublisher);
    }

    private FulfillmentTask taskDueOn(LocalDate expectedDate) {
        AppUser requester = new AppUser();
        requester.setId(UUID.randomUUID());
        CatalogItem item = new CatalogItem();
        item.setName("Clinical Software");
        ServiceRequest sr = new ServiceRequest();
        sr.setId(UUID.randomUUID());
        sr.setOrgId(ORG_ID);
        sr.setNumber("SR-9");
        sr.setCatalogItem(item);
        sr.setRequester(requester);

        FulfillmentTask task = new FulfillmentTask();
        task.setId(UUID.randomUUID());
        task.setServiceRequest(sr);
        task.setDescription("Provision license");
        task.setExpectedDeliveryDate(expectedDate);
        return task;
    }

    private void runWith(FulfillmentTask... tasks) {
        when(fulfillmentTaskRepository.findByExpectedDeliveryDateIsNotNullAndDeliveredAtIsNull())
                .thenReturn(List.of(tasks));
        job.run(TODAY);
    }

    private String phaseFor(FulfillmentTask task) {
        ArgumentCaptor<ServiceRequestEvent> captor = ArgumentCaptor.forClass(ServiceRequestEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ServiceRequestEvent event = captor.getValue();
        assertEquals("FULFILLMENT_REMINDER", event.triggerType());
        assertEquals("SERVICE_REQUEST", event.triggerEntity());
        assertEquals(ORG_ID, event.orgId());
        assertEquals(task.getServiceRequest().getRequester().getId(), event.payload().get("requesterId"));
        assertEquals(com.alignedcardio.itsm.util.DateFormats.formatDate(task.getExpectedDeliveryDate()),
                event.payload().get("expectedDeliveryDate"));
        return (String) event.payload().get("phase");
    }

    @Test
    void threeDaysAwayFiresOnce() {
        FulfillmentTask task = taskDueOn(TODAY.plusDays(3));
        runWith(task);
        assertEquals("THREE_DAYS", phaseFor(task));
        assertEquals(TODAY, task.getLastRemindedOn());
        verify(fulfillmentTaskRepository).save(task);
    }

    @Test
    void oneDayAwayFiresOnce() {
        FulfillmentTask task = taskDueOn(TODAY.plusDays(1));
        runWith(task);
        assertEquals("ONE_DAY", phaseFor(task));
    }

    @Test
    void dueTodayFiresOnce() {
        FulfillmentTask task = taskDueOn(TODAY);
        runWith(task);
        assertEquals("DUE_TODAY", phaseFor(task));
    }

    @Test
    void overdueFiresOnce() {
        FulfillmentTask task = taskDueOn(TODAY.minusDays(2));
        runWith(task);
        assertEquals("OVERDUE", phaseFor(task));
    }

    @Test
    void sameDayRerunIsIdempotent() {
        FulfillmentTask task = taskDueOn(TODAY.plusDays(3));
        runWith(task);
        runWith(task); // second run same day - lastRemindedOn == today, skipped
        verify(eventPublisher, times(1)).publishEvent(any(ServiceRequestEvent.class));
    }

    @Test
    void nonWindowDatesDoNotFire() {
        FulfillmentTask task = taskDueOn(TODAY.plusDays(2));
        runWith(task);
        verifyNoInteractions(eventPublisher);
        assertNull(task.getLastRemindedOn());
    }

    @Test
    void deliveredTaskIsExcludedByQuery() {
        // delivered_at IS NULL is enforced by the repository query - a delivered
        // task is never returned, so no reminder can fire for it.
        FulfillmentTask task = taskDueOn(TODAY.minusDays(5));
        task.setDeliveredAt(OffsetDateTime.now());
        when(fulfillmentTaskRepository.findByExpectedDeliveryDateIsNotNullAndDeliveredAtIsNull())
                .thenReturn(List.of()); // query filters delivered tasks out
        job.run(TODAY);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void nextDayOverdueFiresAgain() {
        FulfillmentTask task = taskDueOn(TODAY.minusDays(1));
        runWith(task);
        // Next day: lastRemindedOn != new today, fires again (daily overdue reminder)
        when(fulfillmentTaskRepository.findByExpectedDeliveryDateIsNotNullAndDeliveredAtIsNull())
                .thenReturn(List.of(task));
        job.run(TODAY.plusDays(1));
        verify(eventPublisher, times(2)).publishEvent(any(ServiceRequestEvent.class));
    }
}
