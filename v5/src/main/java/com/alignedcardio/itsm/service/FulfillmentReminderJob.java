package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.event.ServiceRequestEvent;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.util.DateFormats;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily reminder job for fulfillment delivery dates.
 * Idempotent per day via fulfillment_task.last_reminded_on: a task already
 * reminded today is skipped, so rerunning the job the same day is a no-op.
 * Reminders stop entirely once delivered_at is set (query filters it out).
 */
@Component
public class FulfillmentReminderJob implements Job {

    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public FulfillmentReminderJob(FulfillmentTaskRepository fulfillmentTaskRepository,
                                  ApplicationEventPublisher eventPublisher) {
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        run(LocalDate.now());
    }

    @Transactional
    public void run(LocalDate today) {
        List<FulfillmentTask> candidates =
                fulfillmentTaskRepository.findByExpectedDeliveryDateIsNotNullAndDeliveredAtIsNull();

        for (FulfillmentTask task : candidates) {
            if (today.equals(task.getLastRemindedOn())) {
                continue; // already reminded today - idempotent
            }

            DeliveryReminderClassifier.Reminder reminder =
                    DeliveryReminderClassifier.classify(task.getExpectedDeliveryDate(), today);
            if (reminder == DeliveryReminderClassifier.Reminder.NONE) {
                continue;
            }

            ServiceRequest sr = task.getServiceRequest();
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", sr.getId());
            payload.put("number", sr.getNumber());
            payload.put("catalogItemName", sr.getCatalogItem().getName());
            payload.put("requesterId", sr.getRequester().getId());
            payload.put("taskId", task.getId());
            payload.put("taskDescription", task.getDescription());
            payload.put("expectedDeliveryDate", DateFormats.formatDate(task.getExpectedDeliveryDate()));
            payload.put("phase", reminder.name());

            eventPublisher.publishEvent(
                    new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "FULFILLMENT_REMINDER", payload));

            task.setLastRemindedOn(today);
            fulfillmentTaskRepository.save(task);
        }
    }
}
