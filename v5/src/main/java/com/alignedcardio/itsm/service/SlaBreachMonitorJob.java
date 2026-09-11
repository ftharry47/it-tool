package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.SlaEscalationTier;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.event.SlaBreachEvent;
import com.alignedcardio.itsm.events.SlaBreachStatusChangedEvent;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.SlaEscalationTierRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.TeamRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SlaBreachMonitorJob implements Job {

    private static final Set<Incident.Status> TERMINAL_STATUSES = Set.of(
            Incident.Status.RESOLVED, Incident.Status.CLOSED);

    private final SlaInstanceRepository slaInstanceRepository;
    private final SlaEngine slaEngine;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationService notificationService;
    private final com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder;
    private final SlaEscalationTierRepository escalationTierRepository;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final IncidentRepository incidentRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public SlaBreachMonitorJob(SlaInstanceRepository slaInstanceRepository,
                               SlaEngine slaEngine,
                               ApplicationEventPublisher eventPublisher,
                               NotificationService notificationService,
                               com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder notificationTemplateBuilder,
                               SlaEscalationTierRepository escalationTierRepository,
                               AppUserRepository appUserRepository,
                               TeamRepository teamRepository,
                               IncidentRepository incidentRepository,
                               AuditLogRepository auditLogRepository,
                               ObjectMapper objectMapper) {
        this.slaInstanceRepository = slaInstanceRepository;
        this.slaEngine = slaEngine;
        this.eventPublisher = eventPublisher;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
        this.escalationTierRepository = escalationTierRepository;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.incidentRepository = incidentRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
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

            Incident incident = instance.getIncident();
            // Never escalate a deleted or terminal incident.
            if (incident != null && (incident.getDeletedAt() != null || TERMINAL_STATUSES.contains(incident.getStatus()))) {
                continue;
            }
            if (incident == null && instance.getServiceRequest() != null && instance.getServiceRequest().getDeletedAt() != null) {
                continue;
            }

            evaluateEscalation(instance, incident, now);

            SlaInstance.BreachStatus from = instance.getBreachStatus();
            slaEngine.recalcBreachStatus(instance, now);
            SlaInstance.BreachStatus to = instance.getBreachStatus();

            if (from != to) {
                slaInstanceRepository.save(instance);
                eventPublisher.publishEvent(new SlaBreachStatusChangedEvent(
                        instance.getId(), from, to,
                        instance.getIncident() != null ? instance.getIncident().getId() : null));

                if (incident != null) {
                    eventPublisher.publishEvent(new SlaBreachEvent(
                            incident.getOrgId(),
                            instance.getId(),
                            from != null ? from.name() : "",
                            to != null ? to.name() : "",
                            incident.getId(),
                            incident.getNumber()));
                }

                if (instance.getIncident() == null) {
                    continue;
                }
                Incident breached = instance.getIncident();
                if (to == SlaInstance.BreachStatus.BREACHED && breached.getRequester() != null) {
                    Map<String, Object> breachPayload = new java.util.HashMap<>();
                    breachPayload.put("number", breached.getNumber());
                    breachPayload.put("title", breached.getTitle());
                    breachPayload.put("priority", breached.getPriority() != null ? breached.getPriority().getName() : "");
                    breachPayload.put("targetTime", formatTime(instance.getResolutionDueAt()));
                    breachPayload.put("elapsedTime", formatElapsed(breached.getCreatedAt(), now));
                    breachPayload.put("requesterFirstName", firstName(breached.getRequester().getDisplayName()));
                    breachPayload.put("entityType", "INCIDENT");
                    breachPayload.put("entityId", breached.getId());
                    var content = notificationTemplateBuilder.forEvent("SLA_BREACH", breachPayload);
                    notificationService.send(new NotificationRequest(
                            breached.getOrgId(),
                            breached.getRequester().getId(),
                            "SLA_BREACH",
                            content.inAppSubject(),
                            content.inAppBody(),
                            "INCIDENT",
                            breached.getId(),
                            Notification.Channel.BOTH,
                            content));
                }

                if (to == SlaInstance.BreachStatus.AT_RISK && breached.getAssignee() != null) {
                    Map<String, Object> atRiskPayload = new java.util.HashMap<>();
                    atRiskPayload.put("number", breached.getNumber());
                    atRiskPayload.put("title", breached.getTitle());
                    atRiskPayload.put("priority", breached.getPriority() != null ? breached.getPriority().getName() : "");
                    atRiskPayload.put("targetTime", formatTime(instance.getResolutionDueAt()));
                    atRiskPayload.put("elapsedTime", formatElapsed(breached.getCreatedAt(), now));
                    atRiskPayload.put("assigneeFirstName", firstName(breached.getAssignee().getDisplayName()));
                    atRiskPayload.put("entityType", "INCIDENT");
                    atRiskPayload.put("entityId", breached.getId());
                    var content = notificationTemplateBuilder.forEvent("SLA_AT_RISK", atRiskPayload);
                    notificationService.send(new NotificationRequest(
                            breached.getOrgId(),
                            breached.getAssignee().getId(),
                            "SLA_AT_RISK",
                            content.inAppSubject(),
                            content.inAppBody(),
                            "INCIDENT",
                            breached.getId(),
                            Notification.Channel.BOTH,
                            content));
                }
            }
        }
    }

    /**
     * Advances the instance by exactly one tier per sweep: the lowest-level
     * tier above the current escalation level whose trigger is met fires.
     * Tiers are independent candidates — a higher tier is not blocked by an
     * unfired lower tier (e.g. a resolution-breach tier still fires for a
     * ticket that was never stuck long enough to trip the stuck-status tier).
     * Never fires the same tier twice (escalationLevel is monotonically
     * increasing).
     */
    private void evaluateEscalation(SlaInstance instance, Incident incident, OffsetDateTime now) {
        if (incident == null || instance.getPolicy() == null) {
            return;
        }
        List<SlaEscalationTier> tiers = escalationTierRepository
                .findByPolicyIdOrderByLevelAsc(instance.getPolicy().getId());
        SlaEscalationTier next = tiers.stream()
                .filter(t -> t.getLevel() > instance.getEscalationLevel())
                .filter(t -> triggerMet(t, instance, incident, now))
                .findFirst()
                .orElse(null);
        if (next == null) {
            return;
        }

        notifyTier(next, instance, incident);
        reassignTier(next, incident);

        instance.setEscalationLevel(next.getLevel());
        slaInstanceRepository.save(instance);
    }

    private boolean triggerMet(SlaEscalationTier tier, SlaInstance instance, Incident incident, OffsetDateTime now) {
        return switch (tier.getTriggerType()) {
            case ON_RESPONSE_BREACH -> instance.getResponseDueAt() != null
                    && instance.getResponseMetAt() == null
                    && now.isAfter(instance.getResponseDueAt());
            case ON_RESOLUTION_BREACH -> instance.getResolutionDueAt() != null
                    && instance.getResolutionMetAt() == null
                    && now.isAfter(instance.getResolutionDueAt());
            case ON_STUCK_STATUS -> tier.getStuckStatus() != null
                    && tier.getStuckMinutes() != null
                    && incident.getStatus().name().equals(tier.getStuckStatus())
                    && incident.getUpdatedAt() != null
                    && !incident.getUpdatedAt().plusMinutes(tier.getStuckMinutes()).isAfter(now);
        };
    }

    private void notifyTier(SlaEscalationTier tier, SlaInstance instance, Incident incident) {
        Set<UUID> notified = new HashSet<>();
        Map<String, Object> escPayload = new java.util.HashMap<>();
        escPayload.put("number", incident.getNumber());
        escPayload.put("title", incident.getTitle());
        escPayload.put("tierLevel", tier.getLevel());
        escPayload.put("triggerType", tier.getTriggerType().name());
        escPayload.put("entityType", "INCIDENT");
        escPayload.put("entityId", incident.getId());

        if (tier.getNotifyRole() != null && !tier.getNotifyRole().isBlank()) {
            List<AppUser> recipients = appUserRepository.findByOrgIdAndRoleNames(
                    incident.getOrgId(), List.of(tier.getNotifyRole()));
            for (AppUser recipient : recipients) {
                if (notified.add(recipient.getId())) {
                    var content = notificationTemplateBuilder.forEvent("SLA_ESCALATION", escPayload);
                    notificationService.send(new NotificationRequest(
                            incident.getOrgId(),
                            recipient.getId(),
                            "SLA_ESCALATION",
                            content.inAppSubject(),
                            content.inAppBody(),
                            "INCIDENT",
                            incident.getId(),
                            Notification.Channel.BOTH,
                            content));
                }
            }
        }

        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                incident.getOrgId(), List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            if (notified.add(admin.getId())) {
                var content = notificationTemplateBuilder.forEvent("SLA_ESCALATION_ADMIN", escPayload);
                notificationService.send(new NotificationRequest(
                        incident.getOrgId(),
                        admin.getId(),
                        "SLA_ESCALATION_ADMIN",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "INCIDENT",
                        incident.getId(),
                        Notification.Channel.BOTH,
                        content));
            }
        }
    }

    private void reassignTier(SlaEscalationTier tier, Incident incident) {
        if (tier.getReassignToTeamId() == null) {
            return;
        }
        Team team = teamRepository
                .findByOrgIdAndIdAndDeletedAtIsNull(incident.getOrgId(), tier.getReassignToTeamId())
                .orElse(null);
        if (team != null) {
            // Audit the automatic escalation distinctly from manual ESCALATE_TIER:
            // null actor = system/job, beforeState captures the pre-escalation
            // assignee so performance reports can attribute "escalated away".
            Map<String, Object> beforeState = new java.util.HashMap<>();
            beforeState.put("assigneeId", incident.getAssignee() != null ? incident.getAssignee().getId().toString() : null);
            beforeState.put("assignmentTeamId", incident.getAssignmentTeam() != null ? incident.getAssignmentTeam().getId().toString() : null);
            beforeState.put("assignmentTeamName", incident.getAssignmentTeam() != null ? incident.getAssignmentTeam().getName() : null);

            incident.setAssignmentTeam(team);
            // Move ownership to the new tier; the old agent no longer owns the ticket.
            incident.setAssignee(null);
            incident.setUpdatedAt(OffsetDateTime.now());
            incidentRepository.save(incident);

            Map<String, Object> afterState = new java.util.HashMap<>();
            afterState.put("assignmentTeamId", team.getId().toString());
            afterState.put("assignmentTeamName", team.getName());
            afterState.put("triggerType", tier.getTriggerType().name());
            afterState.put("tierLevel", tier.getLevel());

            try {
                AuditLog log = new AuditLog();
                log.setOrgId(incident.getOrgId());
                log.setActorUserId(null); // system actor — job-triggered
                log.setAction("AUTO_ESCALATE_TIER");
                log.setEntityType("INCIDENT");
                log.setEntityId(incident.getId());
                log.setBeforeState(objectMapper.writeValueAsString(beforeState));
                log.setAfterState(objectMapper.writeValueAsString(afterState));
                auditLogRepository.save(log);
            } catch (Exception e) {
                // Auditing must never break the escalation itself.
            }
        }
    }

    private String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "there";
        return displayName.trim().split("\\s+")[0];
    }

    private String formatTime(OffsetDateTime t) {
        if (t == null) return "unknown";
        return t.format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm"));
    }

    private String formatElapsed(OffsetDateTime start, OffsetDateTime now) {
        if (start == null || now == null) return "unknown";
        Duration d = Duration.between(start, now);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours + "h " + minutes + "m";
    }
}
