package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.repository.BusinessCalendarRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;

@Service
public class SlaEngine {

    private final SlaPolicyRepository slaPolicyRepository;
    private final SlaInstanceRepository slaInstanceRepository;
    private final BusinessCalendarRepository businessCalendarRepository;
    private final BusinessHoursCalculator businessHoursCalculator;

    public SlaEngine(SlaPolicyRepository slaPolicyRepository,
                     SlaInstanceRepository slaInstanceRepository,
                     BusinessCalendarRepository businessCalendarRepository,
                     BusinessHoursCalculator businessHoursCalculator) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.slaInstanceRepository = slaInstanceRepository;
        this.businessCalendarRepository = businessCalendarRepository;
        this.businessHoursCalculator = businessHoursCalculator;
    }

    @Transactional
    public void onIncidentCreated(Incident incident) {
        SlaPolicy policy = findBestPolicy(incident).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return;
        }

        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        ZonedDateTime start = Optional.ofNullable(incident.getCreatedAt())
                .orElse(OffsetDateTime.now())
                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

        ZonedDateTime responseDue = businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResponseTargetMinutes());

        ZonedDateTime resolutionDue = businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResolutionTargetMinutes());

        SlaInstance instance = new SlaInstance();
        instance.setOrgId(incident.getOrgId());
        instance.setPolicy(policy);
        instance.setIncident(incident);
        instance.setResponseDueAt(responseDue.toOffsetDateTime());
        instance.setResolutionDueAt(resolutionDue.toOffsetDateTime());
        instance.setCreatedBy(incident.getCreatedBy());
        instance.setUpdatedBy(incident.getUpdatedBy());

        slaInstanceRepository.save(instance);
    }

    @Transactional
    public void onPriorityChanged(Incident incident) {
        SlaPolicy policy = findBestPolicy(incident).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return;
        }

        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        slaInstanceRepository.findByIncidentId(incident.getId()).ifPresent(instance -> {
            if (instance.getResolutionMetAt() != null) {
                return;
            }

            ZonedDateTime start = Optional.ofNullable(incident.getCreatedAt())
                    .orElse(OffsetDateTime.now())
                    .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

            int paused = instance.getTotalPausedMinutes();

            if (instance.getResponseMetAt() == null) {
                ZonedDateTime responseDue = businessHoursCalculator.addBusinessMinutes(
                        calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                        start, policy.getResponseTargetMinutes() + paused);
                instance.setResponseDueAt(responseDue.toOffsetDateTime());
            }

            ZonedDateTime resolutionDue = businessHoursCalculator.addBusinessMinutes(
                    calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                    start, policy.getResolutionTargetMinutes() + paused);
            instance.setResolutionDueAt(resolutionDue.toOffsetDateTime());

            instance.setPolicy(policy);
            instance.setUpdatedBy(incident.getUpdatedBy());
            instance.setUpdatedAt(OffsetDateTime.now());
            slaInstanceRepository.save(instance);
        });
    }

    private static final Set<Incident.Status> PAUSED_STATUSES = Set.of(
            Incident.Status.ON_HOLD,
            Incident.Status.WAITING_ON_CUSTOMER);

    @Transactional
    public void onStatusChanged(Incident incident) {
        slaInstanceRepository.findByIncidentId(incident.getId()).ifPresent(instance -> {
            OffsetDateTime now = OffsetDateTime.now();

            if (PAUSED_STATUSES.contains(incident.getStatus())) {
                if (instance.getPausedAt() == null) {
                    instance.setPausedAt(now);
                }
            } else {
                if (instance.getPausedAt() != null) {
                    int paused = (int) java.time.Duration.between(instance.getPausedAt(), now).toMinutes();
                    instance.setTotalPausedMinutes(instance.getTotalPausedMinutes() + paused);
                    instance.setPausedAt(null);

                    BusinessCalendar calendar = instance.getPolicy().getBusinessHoursCalendar();
                    if (calendar != null) {
                        ZonedDateTime start = Optional.ofNullable(incident.getCreatedAt())
                                .orElse(now)
                                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

                        if (instance.getResponseMetAt() == null) {
                            ZonedDateTime responseDue = businessHoursCalculator.addBusinessMinutes(
                                    calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                                    start, instance.getPolicy().getResponseTargetMinutes() + instance.getTotalPausedMinutes());
                            instance.setResponseDueAt(responseDue.toOffsetDateTime());
                        }

                        ZonedDateTime resolutionDue = businessHoursCalculator.addBusinessMinutes(
                                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                                start, instance.getPolicy().getResolutionTargetMinutes() + instance.getTotalPausedMinutes());
                        instance.setResolutionDueAt(resolutionDue.toOffsetDateTime());
                    }
                }

                if (incident.getStatus() == Incident.Status.RESOLVED || incident.getStatus() == Incident.Status.CLOSED) {
                    if (instance.getResolutionMetAt() == null) {
                        instance.setResolutionMetAt(now);
                    }
                }

                if (incident.getStatus() == Incident.Status.REOPENED) {
                    instance.setResolutionMetAt(null);
                }
            }

            instance.setUpdatedBy(incident.getUpdatedBy());
            instance.setUpdatedAt(now);
            recalcBreachStatus(instance, now);
            slaInstanceRepository.save(instance);
        });
    }

    private static final Set<ServiceRequest.Status> SR_PAUSED_STATUSES = Set.of(
            ServiceRequest.Status.PENDING_APPROVAL);

    private static final Set<ServiceRequest.Status> SR_TERMINAL_STATUSES = Set.of(
            ServiceRequest.Status.FULFILLED,
            ServiceRequest.Status.REJECTED,
            ServiceRequest.Status.CANCELLED);

    private static final Set<ServiceRequest.Status> SR_FIRST_RESPONSE_STATUSES = Set.of(
            ServiceRequest.Status.APPROVED,
            ServiceRequest.Status.IN_FULFILLMENT,
            ServiceRequest.Status.FULFILLED);

    @Transactional
    public void onServiceRequestCreated(ServiceRequest serviceRequest) {
        SlaPolicy policy = findBestServiceRequestPolicy(serviceRequest).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return;
        }

        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        ZonedDateTime start = Optional.ofNullable(serviceRequest.getCreatedAt())
                .orElse(OffsetDateTime.now())
                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

        ZonedDateTime responseDue = businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResponseTargetMinutes());

        ZonedDateTime resolutionDue = businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResolutionTargetMinutes());

        SlaInstance instance = new SlaInstance();
        instance.setOrgId(serviceRequest.getOrgId());
        instance.setPolicy(policy);
        instance.setServiceRequest(serviceRequest);
        instance.setResponseDueAt(responseDue.toOffsetDateTime());
        instance.setResolutionDueAt(resolutionDue.toOffsetDateTime());
        instance.setCreatedBy(serviceRequest.getCreatedBy());
        instance.setUpdatedBy(serviceRequest.getUpdatedBy());

        slaInstanceRepository.save(instance);
    }

    @Transactional
    public void onServiceRequestStatusChanged(ServiceRequest serviceRequest) {
        slaInstanceRepository.findByServiceRequest_Id(serviceRequest.getId()).ifPresent(instance -> {
            OffsetDateTime now = OffsetDateTime.now();

            if (SR_PAUSED_STATUSES.contains(serviceRequest.getStatus())) {
                if (instance.getPausedAt() == null) {
                    instance.setPausedAt(now);
                }
            } else {
                if (instance.getPausedAt() != null) {
                    int paused = (int) java.time.Duration.between(instance.getPausedAt(), now).toMinutes();
                    instance.setTotalPausedMinutes(instance.getTotalPausedMinutes() + paused);
                    instance.setPausedAt(null);

                    BusinessCalendar calendar = instance.getPolicy().getBusinessHoursCalendar();
                    if (calendar != null) {
                        ZonedDateTime start = Optional.ofNullable(serviceRequest.getCreatedAt())
                                .orElse(now)
                                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

                        if (instance.getResponseMetAt() == null) {
                            ZonedDateTime responseDue = businessHoursCalculator.addBusinessMinutes(
                                    calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                                    start, instance.getPolicy().getResponseTargetMinutes() + instance.getTotalPausedMinutes());
                            instance.setResponseDueAt(responseDue.toOffsetDateTime());
                        }

                        ZonedDateTime resolutionDue = businessHoursCalculator.addBusinessMinutes(
                                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                                start, instance.getPolicy().getResolutionTargetMinutes() + instance.getTotalPausedMinutes());
                        instance.setResolutionDueAt(resolutionDue.toOffsetDateTime());
                    }
                }

                if (SR_TERMINAL_STATUSES.contains(serviceRequest.getStatus())) {
                    if (instance.getResolutionMetAt() == null) {
                        instance.setResolutionMetAt(now);
                    }
                }

                if (SR_FIRST_RESPONSE_STATUSES.contains(serviceRequest.getStatus())) {
                    if (instance.getResponseMetAt() == null) {
                        instance.setResponseMetAt(now);
                    }
                }
            }

            instance.setUpdatedBy(serviceRequest.getUpdatedBy());
            instance.setUpdatedAt(now);
            recalcBreachStatus(instance, now);
            slaInstanceRepository.save(instance);
        });
    }

    @Transactional
    public void recordFirstResponse(Incident incident, OffsetDateTime respondedAt) {
        slaInstanceRepository.findByIncidentId(incident.getId()).ifPresent(instance -> {
            if (instance.getResponseMetAt() == null) {
                instance.setResponseMetAt(respondedAt);
                instance.setUpdatedBy(incident.getUpdatedBy());
                instance.setUpdatedAt(OffsetDateTime.now());
                recalcBreachStatus(instance, OffsetDateTime.now());
                slaInstanceRepository.save(instance);
            }
        });
    }

    public SlaInstance.BreachStatus computeBreachStatus(SlaInstance instance, OffsetDateTime now) {
        if (instance.getResolutionMetAt() != null) {
            return SlaInstance.BreachStatus.ON_TRACK;
        }

        if (instance.getResolutionDueAt() == null && instance.getResponseDueAt() == null) {
            return SlaInstance.BreachStatus.ON_TRACK;
        }

        OffsetDateTime due = instance.getResolutionDueAt() != null ? instance.getResolutionDueAt() : instance.getResponseDueAt();
        if (now.isAfter(due)) {
            return SlaInstance.BreachStatus.BREACHED;
        }

        if (instance.getResponseMetAt() == null && instance.getResponseDueAt() != null) {
            long responseTotal = java.time.Duration.between(
                    Optional.ofNullable(instance.getCreatedAt()).orElse(instance.getResponseDueAt().minusMinutes(1)),
                    instance.getResponseDueAt()).toMinutes();
            long responseElapsed = java.time.Duration.between(
                    Optional.ofNullable(instance.getCreatedAt()).orElse(instance.getResponseDueAt().minusMinutes(1)),
                    now).toMinutes();
            if (responseElapsed > 0 && responseElapsed >= responseTotal * 0.75) {
                return SlaInstance.BreachStatus.AT_RISK;
            }
        }

        long resolutionTotal = java.time.Duration.between(
                Optional.ofNullable(instance.getCreatedAt()).orElse(due.minusMinutes(1)),
                due).toMinutes();
        long resolutionElapsed = java.time.Duration.between(
                Optional.ofNullable(instance.getCreatedAt()).orElse(due.minusMinutes(1)),
                now).toMinutes();
        if (resolutionElapsed > 0 && resolutionElapsed >= resolutionTotal * 0.75) {
            return SlaInstance.BreachStatus.AT_RISK;
        }

        return SlaInstance.BreachStatus.ON_TRACK;
    }

    void recalcBreachStatus(SlaInstance instance, OffsetDateTime now) {
        SlaInstance.BreachStatus next = computeBreachStatus(instance, now);
        instance.setBreachStatus(next);
    }

    private Optional<SlaPolicy> findBestPolicy(Incident incident) {
        String priorityName = incident.getPriority() != null ? incident.getPriority().getName() : null;
        return slaPolicyRepository.findByOrgIdAndAppliesTo(incident.getOrgId(), SlaPolicy.AppliesTo.INCIDENT)
                .stream()
                .filter(p -> p.getBusinessHoursCalendar() != null)
                .filter(p -> p.getPriorityFilter() == null || p.getPriorityFilter().equals(priorityName))
                .findFirst();
    }

    private Optional<SlaPolicy> findBestServiceRequestPolicy(ServiceRequest serviceRequest) {
        return slaPolicyRepository.findByOrgIdAndAppliesTo(serviceRequest.getOrgId(), SlaPolicy.AppliesTo.REQUEST)
                .stream()
                .filter(p -> p.getBusinessHoursCalendar() != null)
                .findFirst();
    }
}
