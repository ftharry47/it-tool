package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.event.SlaBreachEvent;
import com.alignedcardio.itsm.events.SlaBreachStatusChangedEvent;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Component
public class SlaBreachMonitorJob implements Job {

    private final SlaInstanceRepository slaInstanceRepository;
    private final SlaEngine slaEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;

    public SlaBreachMonitorJob(SlaInstanceRepository slaInstanceRepository,
                               SlaEngine slaEngine,
                               ApplicationEventPublisher eventPublisher,
                               NotificationService notificationService) {
        this.slaInstanceRepository = slaInstanceRepository;
        this.slaEngine = slaEngine;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void execute(JobExecutionContext context) {
        OffsetDateTime now = OffsetDateTime.now();
        List<SlaInstance> active = slaInstanceRepository.findByBreachStatusIn(
                List.of(SlaInstance.BreachStatus.ON_TRACK, SlaInstance.BreachStatus.AT_RISK));

        for (SlaInstance instance : active) {
            if (instance.getResolutionMetAt() != null) {
                continue;
            }

            SlaInstance.BreachStatus from = instance.getBreachStatus();
            slaEngine.recalcBreachStatus(instance, now);
            SlaInstance.BreachStatus to = instance.getBreachStatus();

            if (from != to) {
                slaInstanceRepository.save(instance);
                eventPublisher.publishEvent(new SlaBreachStatusChangedEvent(
                        instance.getId(), from, to,
                        instance.getIncident() != null ? instance.getIncident().getId() : null));

                if (instance.getIncident() != null) {
                    Incident incident = instance.getIncident();
                    eventPublisher.publishEvent(new SlaBreachEvent(
                            incident.getOrgId(),
                            instance.getId(),
                            from != null ? from.name() : "",
                            to != null ? to.name() : "",
                            incident.getId(),
                            incident.getNumber()));
                }

                if (to == SlaInstance.BreachStatus.BREACHED && instance.getIncident() != null
                        && instance.getIncident().getRequester() != null) {
                    Incident incident = instance.getIncident();
                    notificationService.send(new NotificationRequest(
                            incident.getOrgId(),
                            incident.getRequester().getId(),
                            "SLA_BREACH",
                            "SLA breached for " + incident.getNumber(),
                            "The SLA for incident " + incident.getNumber() + " has been breached.",
                            "INCIDENT",
                            incident.getId(),
                            Notification.Channel.BOTH));
                }
            }
        }
    }
}
