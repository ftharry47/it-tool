package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.SlaEscalationTier;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.Team;
import com.alignedcardio.itsm.event.SlaBreachEvent;
import com.alignedcardio.itsm.events.SlaBreachStatusChangedEvent;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.IncidentCommentRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.SlaEscalationTierRepository;
import com.alignedcardio.itsm.repository.ServiceRequestCommentRepository;
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
import com.alignedcardio.itsm.util.DateFormats;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SlaBreachMonitorJob implements Job {

    private static final Set<Incident.Status> TERMINAL_STATUSES = Set.of(
            Incident.Status.RESOLVED, Incident.Status.CLOSED);
    private static final Set<Problem.Status> PROBLEM_TERMINAL_STATUSES = Set.of(
            Problem.Status.RESOLVED, Problem.Status.CLOSED);
    private static final Set<ChangeRequest.Status> CHANGE_TERMINAL_STATUSES = Set.of(
            ChangeRequest.Status.COMPLETED, ChangeRequest.Status.FAILED,
            ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED,
            ChangeRequest.Status.CLOSED, ChangeRequest.Status.REJECTED);

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
    private final IncidentCommentRepository incidentCommentRepository;
    private final ServiceRequestCommentRepository serviceRequestCommentRepository;
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
                               IncidentCommentRepository incidentCommentRepository,
                               ServiceRequestCommentRepository serviceRequestCommentRepository,
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
        this.incidentCommentRepository = incidentCommentRepository;
        this.serviceRequestCommentRepository = serviceRequestCommentRepository;
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

            // Resolve whichever ticket type backs the instance; null means the
            // linked ticket is deleted, terminal, or an orphaned FK — skip it.
            TicketRef ref = ticketRef(instance);
            if (ref == null) {
                continue;
            }
            Incident incident = instance.getIncident();

            // A paused SLA (ON_HOLD / waiting-on-customer) must never be
            // evaluated against wall-clock due dates — the pause only shifts
            // dueAt forward when the ticket unpauses, so evaluating now would
            // breach/escalate a clock that is legitimately stopped.
            if (instance.getPausedAt() != null) {
                continue;
            }

            evaluateEscalation(instance, ref, incident, now);

            SlaInstance.BreachStatus from = instance.getBreachStatus();
            slaEngine.recalcBreachStatus(instance, now);
            SlaInstance.BreachStatus to = instance.getBreachStatus();

            if (from != to) {
                slaInstanceRepository.save(instance);
                eventPublisher.publishEvent(new SlaBreachStatusChangedEvent(
                        instance.getId(), from, to,
                        incident != null ? incident.getId() : null));

                if (incident != null) {
                    eventPublisher.publishEvent(new SlaBreachEvent(
                            incident.getOrgId(),
                            instance.getId(),
                            from != null ? from.name() : "",
                            to != null ? to.name() : "",
                            incident.getId(),
                            incident.getNumber()));
                }

                // Service requests keep their historical behaviour — breach
                // recalculation only, no end-user-facing notifications.
                if ("SERVICE_REQUEST".equals(ref.entityType())) {
                    continue;
                }

                if (to == SlaInstance.BreachStatus.BREACHED) {
                    AppUser notify = ref.requester() != null ? ref.requester() : ref.assignee();
                    if (notify != null) {
                        Map<String, Object> breachPayload = new java.util.HashMap<>();
                        breachPayload.put("number", ref.number());
                        breachPayload.put("title", ref.title());
                        breachPayload.put("priority", ref.priorityName());
                        breachPayload.put("targetTime", formatTime(instance.getResolutionDueAt()));
                        breachPayload.put("elapsedTime", formatElapsed(ref.createdAt(), now));
                        breachPayload.put("requesterFirstName", firstName(notify.getDisplayName()));
                        breachPayload.put("entityType", ref.entityType());
                        breachPayload.put("entityId", ref.id());
                        var content = notificationTemplateBuilder.forEvent("SLA_BREACH", breachPayload);
                        notificationService.send(new NotificationRequest(
                                ref.orgId(),
                                notify.getId(),
                                "SLA_BREACH",
                                content.inAppSubject(),
                                content.inAppBody(),
                                ref.entityType(),
                                ref.id(),
                                Notification.Channel.BOTH,
                                content));
                    }
                }

                if (to == SlaInstance.BreachStatus.AT_RISK && ref.assignee() != null) {
                    Map<String, Object> atRiskPayload = new java.util.HashMap<>();
                    atRiskPayload.put("number", ref.number());
                    atRiskPayload.put("title", ref.title());
                    atRiskPayload.put("priority", ref.priorityName());
                    atRiskPayload.put("targetTime", formatTime(instance.getResolutionDueAt()));
                    atRiskPayload.put("elapsedTime", formatElapsed(ref.createdAt(), now));
                    atRiskPayload.put("assigneeFirstName", firstName(ref.assignee().getDisplayName()));
                    atRiskPayload.put("entityType", ref.entityType());
                    atRiskPayload.put("entityId", ref.id());
                    var content = notificationTemplateBuilder.forEvent("SLA_AT_RISK", atRiskPayload);
                    notificationService.send(new NotificationRequest(
                            ref.orgId(),
                            ref.assignee().getId(),
                            "SLA_AT_RISK",
                            content.inAppSubject(),
                            content.inAppBody(),
                            ref.entityType(),
                            ref.id(),
                            Notification.Channel.BOTH,
                            content));
                }
            }
        }
    }

    /**
     * Normalised view over the four SLA-backed ticket types so escalation and
     * notification logic can treat them uniformly.
     */
    private record TicketRef(UUID id, String number, String title, String status,
                             OffsetDateTime createdAt, OffsetDateTime updatedAt,
                             UUID orgId, AppUser assignee, AppUser requester,
                             String entityType, String priorityName) {}

    private TicketRef ticketRef(SlaInstance instance) {
        try {
            Incident i = instance.getIncident();
            if (i != null) {
                if (i.getDeletedAt() != null || TERMINAL_STATUSES.contains(i.getStatus())) return null;
                return new TicketRef(i.getId(), "INC-" + i.getNumber(), i.getTitle(), i.getStatus().name(),
                        i.getCreatedAt(), i.getUpdatedAt(), i.getOrgId(), i.getAssignee(), i.getRequester(),
                        "INCIDENT", i.getPriority() != null ? i.getPriority().getName() : "");
            }
            ServiceRequest sr = instance.getServiceRequest();
            if (sr != null) {
                if (sr.getDeletedAt() != null) return null;
                return new TicketRef(sr.getId(), sr.getNumber(),
                        sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "Service request",
                        sr.getStatus() != null ? sr.getStatus().name() : "",
                        sr.getCreatedAt(), sr.getUpdatedAt(), sr.getOrgId(), null, sr.getRequester(),
                        "SERVICE_REQUEST", "");
            }
            Problem p = instance.getProblem();
            if (p != null) {
                if (p.getDeletedAt() != null || PROBLEM_TERMINAL_STATUSES.contains(p.getStatus())) return null;
                return new TicketRef(p.getId(), p.getNumber(), p.getTitle(), p.getStatus().name(),
                        p.getCreatedAt(), p.getUpdatedAt(), p.getOrgId(), p.getAssignee(), null,
                        "PROBLEM", "");
            }
            ChangeRequest c = instance.getChangeRequest();
            if (c != null) {
                if (c.getDeletedAt() != null || CHANGE_TERMINAL_STATUSES.contains(c.getStatus())) return null;
                return new TicketRef(c.getId(), c.getNumber(), c.getTitle(), c.getStatus().name(),
                        c.getCreatedAt(), c.getUpdatedAt(), c.getOrgId(), c.getAssignee(), c.getRequestedBy(),
                        "CHANGE", c.getRisk() != null ? c.getRisk().name() : "");
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            // Lazy association backed by a soft-deleted row (@Where) — treat as gone.
            return null;
        }
        return null;
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
    private void evaluateEscalation(SlaInstance instance, TicketRef ref, Incident incident, OffsetDateTime now) {
        if (ref == null || instance.getPolicy() == null) {
            return;
        }
        List<SlaEscalationTier> tiers = escalationTierRepository
                .findByPolicyIdOrderByLevelAsc(instance.getPolicy().getId());
        SlaEscalationTier next = tiers.stream()
                .filter(t -> t.getLevel() > instance.getEscalationLevel())
                .filter(t -> triggerMet(t, instance, ref, now))
                .findFirst()
                .orElse(null);
        if (next == null) {
            return;
        }

        notifyTier(next, instance, ref);
        // Team reassignment is incident-only — problems/changes have no
        // assignment team; for them the tier only notifies.
        if (incident != null) {
            reassignTier(next, incident);
        }

        instance.setEscalationLevel(next.getLevel());
        slaInstanceRepository.save(instance);
    }

    private boolean triggerMet(SlaEscalationTier tier, SlaInstance instance, TicketRef ref, OffsetDateTime now) {
        return switch (tier.getTriggerType()) {
            case ON_RESPONSE_BREACH -> instance.getResponseDueAt() != null
                    && instance.getResponseMetAt() == null
                    && now.isAfter(instance.getResponseDueAt());
            case ON_RESOLUTION_BREACH -> instance.getResolutionDueAt() != null
                    && instance.getResolutionMetAt() == null
                    && now.isAfter(instance.getResolutionDueAt());
            // Paused statuses (ON_HOLD etc.) are excluded even if an admin
            // configures one as a stuck trigger — a deliberately paused clock
            // is not neglect.
            // "Stuck" means no meaningful ACTIVITY for stuckMinutes — not just
            // no status change. updated_at is DB-insert-only and isn't bumped
            // by assignment or comments, so it alone would penalize tickets
            // being actively worked. Last activity = latest of updatedAt,
            // any audit entry (assignment, escalation, updates), and comments.
            case ON_STUCK_STATUS -> tier.getStuckStatus() != null
                    && tier.getStuckMinutes() != null
                    && instance.getPausedAt() == null
                    && tier.getStuckStatus().equals(ref.status())
                    && !lastActivityAt(ref).plusMinutes(tier.getStuckMinutes()).isAfter(now);
        };
    }

    /**
     * Most recent meaningful activity on the ticket: the later of its
     * updatedAt, the newest audit-log entry (assignments and field updates
     * are audited even though they don't bump updatedAt), and the newest
     * comment for comment-capable entities. Falls back to createdAt when
     * nothing has ever happened — a genuinely neglected ticket still
     * escalates on schedule.
     */
    private OffsetDateTime lastActivityAt(TicketRef ref) {
        OffsetDateTime last = ref.updatedAt() != null ? ref.updatedAt()
                : ref.createdAt() != null ? ref.createdAt()
                : OffsetDateTime.now();
        OffsetDateTime audit = auditLogRepository.findMaxCreatedAtByEntity(
                ref.orgId(), ref.entityType(), ref.id());
        if (audit != null && audit.isAfter(last)) {
            last = audit;
        }
        OffsetDateTime comment = switch (ref.entityType()) {
            case "INCIDENT" -> incidentCommentRepository.findMaxCreatedAtByIncidentId(ref.id());
            case "SERVICE_REQUEST" -> serviceRequestCommentRepository.findMaxCreatedAtByServiceRequestId(ref.id());
            default -> null;
        };
        if (comment != null && comment.isAfter(last)) {
            last = comment;
        }
        return last;
    }

    private void notifyTier(SlaEscalationTier tier, SlaInstance instance, TicketRef ref) {
        Set<UUID> notified = new HashSet<>();
        Map<String, Object> escPayload = new java.util.HashMap<>();
        escPayload.put("number", ref.number());
        escPayload.put("title", ref.title());
        escPayload.put("tierLevel", tier.getLevel());
        escPayload.put("triggerType", tier.getTriggerType().name());
        escPayload.put("entityType", ref.entityType());
        escPayload.put("entityId", ref.id());

        if (tier.getNotifyRole() != null && !tier.getNotifyRole().isBlank()) {
            List<AppUser> recipients = appUserRepository.findByOrgIdAndRoleNames(
                    ref.orgId(), List.of(tier.getNotifyRole()));
            for (AppUser recipient : recipients) {
                if (notified.add(recipient.getId())) {
                    var content = notificationTemplateBuilder.forEvent("SLA_ESCALATION", escPayload);
                    notificationService.send(new NotificationRequest(
                            ref.orgId(),
                            recipient.getId(),
                            "SLA_ESCALATION",
                            content.inAppSubject(),
                            content.inAppBody(),
                            ref.entityType(),
                            ref.id(),
                            Notification.Channel.BOTH,
                            content));
                }
            }
        }

        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                ref.orgId(), List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            if (notified.add(admin.getId())) {
                var content = notificationTemplateBuilder.forEvent("SLA_ESCALATION_ADMIN", escPayload);
                notificationService.send(new NotificationRequest(
                        ref.orgId(),
                        admin.getId(),
                        "SLA_ESCALATION_ADMIN",
                        content.inAppSubject(),
                        content.inAppBody(),
                        ref.entityType(),
                        ref.id(),
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
            Team current = incident.getAssignmentTeam();
            // Never let a policy tier move a ticket DOWN or SIDEWAYS the
            // support-tier chain — manual escalation may already have moved it
            // past the tier's target team (slaInstance.escalationLevel tracks
            // policy-tier progression, not the ticket's actual tier position).
            int currentIdx = SupportTiers.indexOf(current);
            int targetIdx = SupportTiers.indexOf(team);
            boolean downgradeSuppressed = currentIdx >= 0 && targetIdx >= 0 && currentIdx >= targetIdx;

            Map<String, Object> beforeState = new java.util.HashMap<>();
            beforeState.put("assigneeId", incident.getAssignee() != null ? incident.getAssignee().getId().toString() : null);
            beforeState.put("assignmentTeamId", current != null ? current.getId().toString() : null);
            beforeState.put("assignmentTeamName", current != null ? current.getName() : null);

            if (!downgradeSuppressed) {
                incident.setAssignmentTeam(team);
                // Move ownership to the new tier; the old agent no longer owns the ticket.
                incident.setAssignee(null);
                incident.setUpdatedAt(OffsetDateTime.now());
                incidentRepository.save(incident);
            }

            Map<String, Object> afterState = new java.util.HashMap<>();
            afterState.put("assignmentTeamId", downgradeSuppressed && current != null
                    ? current.getId().toString() : team.getId().toString());
            afterState.put("assignmentTeamName", downgradeSuppressed && current != null
                    ? current.getName() : team.getName());
            afterState.put("triggerType", tier.getTriggerType().name());
            afterState.put("tierLevel", tier.getLevel());
            if (downgradeSuppressed) {
                afterState.put("downgradeSuppressed", true);
                afterState.put("suppressedReason", currentIdx == targetIdx ? "SAME_TIER" : "DOWNGRADE");
            }

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
        // Server-side rendering, fixed US Eastern — matches in-app display.
        return DateFormats.formatDateTime(t);
    }

    private String formatElapsed(OffsetDateTime start, OffsetDateTime now) {
        if (start == null || now == null) return "unknown";
        Duration d = Duration.between(start, now);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours + "h " + minutes + "m";
    }
}
