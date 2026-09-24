package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
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
import java.util.UUID;

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
    public boolean onIncidentCreated(Incident incident) {
        return onIncidentCreated(incident, null);
    }

    /**
     * anchor overrides the clock start (used by SLA reset so recreated
     * instances measure from reset time, not the original createdAt).
     */
    @Transactional
    public boolean onIncidentCreated(Incident incident, OffsetDateTime anchor) {
        SlaPolicy policy = findBestPolicy(incident).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return false;
        }

        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        ZonedDateTime start = Optional.ofNullable(anchor != null ? anchor : incident.getCreatedAt())
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
        // A recreated clock for a ticket already in a paused status must start
        // paused — otherwise it would breach while legitimately held.
        if (PAUSED_STATUSES.contains(incident.getStatus())) {
            instance.setPausedAt(OffsetDateTime.now());
        }

        slaInstanceRepository.save(instance);
        return true;
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

            // First engagement = any non-NEW status (assignment, triage, hold,
            // work, terminal). Previously only a PUBLIC comment set this, so
            // tickets handled purely via status changes kept "calculating" a
            // response clock forever — even after closure. Mirrors the
            // response-status sets used for SRs, problems, and changes.
            if (incident.getStatus() != Incident.Status.NEW
                    && instance.getResponseMetAt() == null) {
                instance.setResponseMetAt(now);
            }

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
            ServiceRequest.Status.PENDING_APPROVAL,
            ServiceRequest.Status.ON_HOLD);

    private static final Set<ServiceRequest.Status> SR_TERMINAL_STATUSES = Set.of(
            ServiceRequest.Status.FULFILLED,
            ServiceRequest.Status.REJECTED,
            ServiceRequest.Status.CANCELLED);

    private static final Set<ServiceRequest.Status> SR_FIRST_RESPONSE_STATUSES = Set.of(
            ServiceRequest.Status.APPROVED,
            ServiceRequest.Status.IN_FULFILLMENT,
            ServiceRequest.Status.FULFILLED);

    @Transactional
    public boolean onServiceRequestCreated(ServiceRequest serviceRequest) {
        return onServiceRequestCreated(serviceRequest, null);
    }

    @Transactional
    public boolean onServiceRequestCreated(ServiceRequest serviceRequest, OffsetDateTime anchor) {
        SlaPolicy policy = findBestServiceRequestPolicy(serviceRequest).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return false;
        }

        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        ZonedDateTime start = Optional.ofNullable(anchor != null ? anchor : serviceRequest.getCreatedAt())
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
        if (SR_PAUSED_STATUSES.contains(serviceRequest.getStatus())) {
            instance.setPausedAt(OffsetDateTime.now());
        }

        slaInstanceRepository.save(instance);
        return true;
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

    // --- Problems ---------------------------------------------------------

    private static final Set<Problem.Status> PROBLEM_RESPONSE_STATUSES = Set.of(
            Problem.Status.INVESTIGATING, Problem.Status.KNOWN_ERROR,
            Problem.Status.RESOLVED, Problem.Status.CLOSED);

    private static final Set<Problem.Status> PROBLEM_TERMINAL_STATUSES = Set.of(
            Problem.Status.RESOLVED, Problem.Status.CLOSED);

    @Transactional
    public boolean onProblemCreated(Problem problem) {
        return onProblemCreated(problem, null);
    }

    @Transactional
    public boolean onProblemCreated(Problem problem, OffsetDateTime anchor) {
        SlaPolicy policy = findBestProblemPolicy(problem).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return false;
        }
        slaInstanceRepository.save(newInstance(
                problem.getOrgId(), policy,
                anchor != null ? anchor : problem.getCreatedAt(),
                problem.getCreatedBy(), problem.getUpdatedBy(), i -> i.setProblem(problem)));
        return true;
    }

    /** Response = time to first investigation; resolution = RESOLVED/CLOSED. */
    @Transactional
    public void onProblemStatusChanged(Problem problem) {
        slaInstanceRepository.findByProblem_Id(problem.getId()).ifPresent(instance ->
                applyStatusChange(instance, problem.getCreatedAt(), false,
                        PROBLEM_RESPONSE_STATUSES.contains(problem.getStatus()),
                        PROBLEM_TERMINAL_STATUSES.contains(problem.getStatus()),
                        problem.getUpdatedBy(), OffsetDateTime.now()));
    }

    // --- Changes ----------------------------------------------------------

    private static final Set<ChangeRequest.Status> CHANGE_RESPONSE_STATUSES = Set.of(
            ChangeRequest.Status.APPROVED, ChangeRequest.Status.IN_PROGRESS,
            ChangeRequest.Status.COMPLETED, ChangeRequest.Status.CLOSED);

    private static final Set<ChangeRequest.Status> CHANGE_TERMINAL_STATUSES = Set.of(
            ChangeRequest.Status.COMPLETED, ChangeRequest.Status.FAILED,
            ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED,
            ChangeRequest.Status.CLOSED, ChangeRequest.Status.REJECTED);

    @Transactional
    public boolean onChangeCreated(ChangeRequest change) {
        return onChangeCreated(change, null);
    }

    @Transactional
    public boolean onChangeCreated(ChangeRequest change, OffsetDateTime anchor) {
        SlaPolicy policy = findBestChangePolicy(change).orElse(null);
        if (policy == null || policy.getBusinessHoursCalendar() == null) {
            return false;
        }
        slaInstanceRepository.save(newInstance(
                change.getOrgId(), policy,
                anchor != null ? anchor : change.getCreatedAt(),
                change.getCreatedBy(), change.getUpdatedBy(), i -> i.setChangeRequest(change)));
        return true;
    }

    /** Response = time to approval; resolution = time to a terminal implementation state. */
    @Transactional
    public void onChangeStatusChanged(ChangeRequest change) {
        slaInstanceRepository.findByChangeRequest_Id(change.getId()).ifPresent(instance ->
                applyStatusChange(instance, change.getCreatedAt(), false,
                        CHANGE_RESPONSE_STATUSES.contains(change.getStatus()),
                        CHANGE_TERMINAL_STATUSES.contains(change.getStatus()),
                        change.getUpdatedBy(), OffsetDateTime.now()));
    }

    // --- shared machinery -------------------------------------------------

    private SlaInstance newInstance(UUID orgId, SlaPolicy policy, OffsetDateTime createdAt,
                                    UUID createdBy, UUID updatedBy,
                                    java.util.function.Consumer<SlaInstance> ticketLink) {
        BusinessCalendar calendar = policy.getBusinessHoursCalendar();
        ZonedDateTime start = Optional.ofNullable(createdAt)
                .orElse(OffsetDateTime.now())
                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));

        SlaInstance instance = new SlaInstance();
        instance.setOrgId(orgId);
        instance.setPolicy(policy);
        ticketLink.accept(instance);
        instance.setResponseDueAt(businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResponseTargetMinutes()).toOffsetDateTime());
        instance.setResolutionDueAt(businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, policy.getResolutionTargetMinutes()).toOffsetDateTime());
        instance.setCreatedBy(createdBy);
        instance.setUpdatedBy(updatedBy);
        return instance;
    }

    /**
     * Shared pause/mark-met/breach-recalc logic for ticket types whose SLA
     * lifecycle is a pure status function (problems and changes — neither
     * pauses nor reopens today).
     */
    private void applyStatusChange(SlaInstance instance, OffsetDateTime ticketCreatedAt,
                                   boolean paused, boolean markResponse, boolean markResolution,
                                   UUID updatedBy, OffsetDateTime now) {
        if (paused) {
            if (instance.getPausedAt() == null) {
                instance.setPausedAt(now);
            }
        } else {
            if (instance.getPausedAt() != null) {
                unpauseAndRecalcDueDates(instance, ticketCreatedAt, now);
            }
            if (markResolution && instance.getResolutionMetAt() == null) {
                instance.setResolutionMetAt(now);
            }
            if (markResponse && instance.getResponseMetAt() == null) {
                instance.setResponseMetAt(now);
            }
        }
        instance.setUpdatedBy(updatedBy);
        instance.setUpdatedAt(now);
        recalcBreachStatus(instance, now);
        slaInstanceRepository.save(instance);
    }

    private void unpauseAndRecalcDueDates(SlaInstance instance, OffsetDateTime ticketCreatedAt,
                                          OffsetDateTime now) {
        int paused = (int) java.time.Duration.between(instance.getPausedAt(), now).toMinutes();
        instance.setTotalPausedMinutes(instance.getTotalPausedMinutes() + paused);
        instance.setPausedAt(null);

        BusinessCalendar calendar = instance.getPolicy().getBusinessHoursCalendar();
        if (calendar == null) {
            return;
        }
        ZonedDateTime start = Optional.ofNullable(ticketCreatedAt)
                .orElse(now)
                .atZoneSameInstant(ZoneId.of(calendar.getTimezone()));
        if (instance.getResponseMetAt() == null) {
            instance.setResponseDueAt(businessHoursCalculator.addBusinessMinutes(
                    calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                    start, instance.getPolicy().getResponseTargetMinutes() + instance.getTotalPausedMinutes())
                    .toOffsetDateTime());
        }
        instance.setResolutionDueAt(businessHoursCalculator.addBusinessMinutes(
                calendar.getTimezone(), calendar.getWorkingHours(), calendar.getHolidays(),
                start, instance.getPolicy().getResolutionTargetMinutes() + instance.getTotalPausedMinutes())
                .toOffsetDateTime());
    }

    private Optional<SlaPolicy> findBestProblemPolicy(Problem problem) {
        return slaPolicyRepository.findByOrgIdAndAppliesTo(problem.getOrgId(), SlaPolicy.AppliesTo.PROBLEM)
                .stream()
                .filter(p -> p.getBusinessHoursCalendar() != null)
                .findFirst();
    }

    /** priorityFilter on CHANGE policies matches the change's risk level. */
    private Optional<SlaPolicy> findBestChangePolicy(ChangeRequest change) {
        String risk = change.getRisk() != null ? change.getRisk().name() : null;
        return slaPolicyRepository.findByOrgIdAndAppliesTo(change.getOrgId(), SlaPolicy.AppliesTo.CHANGE)
                .stream()
                .filter(p -> p.getBusinessHoursCalendar() != null)
                .filter(p -> p.getPriorityFilter() == null || p.getPriorityFilter().equals(risk))
                .findFirst();
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
        String priorityName = serviceRequest.getPriority() != null ? serviceRequest.getPriority().getName() : null;
        return slaPolicyRepository.findByOrgIdAndAppliesTo(serviceRequest.getOrgId(), SlaPolicy.AppliesTo.REQUEST)
                .stream()
                .filter(p -> p.getBusinessHoursCalendar() != null)
                .filter(p -> p.getPriorityFilter() == null || p.getPriorityFilter().equals(priorityName))
                .findFirst();
    }
}
