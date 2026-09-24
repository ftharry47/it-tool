package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.admin.BusinessCalendarRequest;
import com.alignedcardio.itsm.api.admin.SlaPolicyRequest;
import com.alignedcardio.itsm.api.admin.SlaPolicyResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BusinessCalendar;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.SlaEscalationTier;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.entity.SlaPolicy;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.BusinessCalendarRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.SlaEscalationTierRepository;
import com.alignedcardio.itsm.repository.SlaInstanceRepository;
import com.alignedcardio.itsm.repository.SlaPolicyRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class SlaAdminService {

    private final SlaPolicyRepository slaPolicyRepository;
    private final BusinessCalendarRepository businessCalendarRepository;
    private final SlaInstanceRepository slaInstanceRepository;
    private final SlaEscalationTierRepository escalationTierRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final AppUserRepository appUserRepository;
    private final NotificationService notificationService;
    private final NotificationTemplateBuilder notificationTemplateBuilder;

    public SlaAdminService(SlaPolicyRepository slaPolicyRepository,
                           BusinessCalendarRepository businessCalendarRepository,
                           SlaInstanceRepository slaInstanceRepository,
                           SlaEscalationTierRepository escalationTierRepository,
                           TeamMemberRepository teamMemberRepository,
                           FulfillmentTaskRepository fulfillmentTaskRepository,
                           AppUserRepository appUserRepository,
                           NotificationService notificationService,
                           NotificationTemplateBuilder notificationTemplateBuilder) {
        this.slaPolicyRepository = slaPolicyRepository;
        this.businessCalendarRepository = businessCalendarRepository;
        this.slaInstanceRepository = slaInstanceRepository;
        this.escalationTierRepository = escalationTierRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.appUserRepository = appUserRepository;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
    }

    @Transactional(readOnly = true)
    public List<SlaPolicyResponse> listPolicies(UUID orgId) {
        // Return every policy — REQUEST/PROBLEM/CHANGE policies are managed here
        // too; filtering to INCIDENT hid them from the admin UI entirely.
        return slaPolicyRepository.findByOrgId(orgId)
                .stream().map(this::toResponse).toList();
    }

    private SlaPolicyResponse toResponse(SlaPolicy p) {
        SlaPolicyResponse.CalendarRef calendar = p.getBusinessHoursCalendar() == null
                ? null
                : new SlaPolicyResponse.CalendarRef(
                        p.getBusinessHoursCalendar().getId(),
                        p.getBusinessHoursCalendar().getName());
        return new SlaPolicyResponse(
                p.getId(),
                p.getName(),
                p.getAppliesTo() == null ? null : p.getAppliesTo().name(),
                p.getPriorityFilter(),
                p.getWorkflowType(),
                p.getResponseTargetMinutes(),
                p.getResolutionTargetMinutes(),
                calendar);
    }

    @Transactional
    public SlaPolicyResponse createPolicy(UUID orgId, UUID userId, SlaPolicyRequest request) {
        SlaPolicy policy = new SlaPolicy();
        policy.setOrgId(orgId);
        policy.setName(request.name());
        policy.setAppliesTo(request.appliesTo());
        policy.setPriorityFilter(request.priorityFilter());
        policy.setWorkflowType(normalizeWorkflowType(request));
        policy.setResponseTargetMinutes(request.responseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        if (request.businessHoursCalendarId() != null) {
            BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(request.businessHoursCalendarId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Business calendar not found"));
            policy.setBusinessHoursCalendar(calendar);
        }
        policy.setCreatedBy(userId);
        policy.setUpdatedBy(userId);
        return toResponse(slaPolicyRepository.save(policy));
    }

    @Transactional
    public SlaPolicyResponse updatePolicy(UUID orgId, UUID policyId, UUID userId, SlaPolicyRequest request) {
        SlaPolicy policy = slaPolicyRepository.findByIdAndOrgId(policyId, orgId)
                .orElseThrow(() -> new NotFoundException("SLA policy not found"));

        // Capture a human-readable diff before mutating so the notification
        // tells agents exactly what changed.
        List<String> diffs = new ArrayList<>();
        if (!Objects.equals(policy.getName(), request.name())) {
            diffs.add("renamed '" + policy.getName() + "' → '" + request.name() + "'");
        }
        if (!Objects.equals(policy.getResponseTargetMinutes(), request.responseTargetMinutes())) {
            diffs.add("response target " + fmtMinutes(policy.getResponseTargetMinutes())
                    + " → " + fmtMinutes(request.responseTargetMinutes()));
        }
        if (!Objects.equals(policy.getResolutionTargetMinutes(), request.resolutionTargetMinutes())) {
            diffs.add("resolution target " + fmtMinutes(policy.getResolutionTargetMinutes())
                    + " → " + fmtMinutes(request.resolutionTargetMinutes()));
        }
        if (!Objects.equals(policy.getPriorityFilter(), request.priorityFilter())) {
            diffs.add("priority filter " + orAll(policy.getPriorityFilter())
                    + " → " + orAll(request.priorityFilter()));
        }
        if (!Objects.equals(policy.getWorkflowType(), normalizeWorkflowType(request))) {
            diffs.add("workflow type " + orAll(policy.getWorkflowType())
                    + " → " + orAll(request.workflowType()));
        }

        policy.setName(request.name());
        policy.setAppliesTo(request.appliesTo());
        policy.setPriorityFilter(request.priorityFilter());
        policy.setWorkflowType(normalizeWorkflowType(request));
        policy.setResponseTargetMinutes(request.responseTargetMinutes());
        policy.setResolutionTargetMinutes(request.resolutionTargetMinutes());
        if (request.businessHoursCalendarId() != null) {
            BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(request.businessHoursCalendarId(), orgId)
                    .orElseThrow(() -> new NotFoundException("Business calendar not found"));
            policy.setBusinessHoursCalendar(calendar);
        } else {
            policy.setBusinessHoursCalendar(null);
        }
        policy.setUpdatedBy(userId);
        SlaPolicy saved = slaPolicyRepository.save(policy);
        notifyPolicyChange(saved, userId, "updated", String.join(", ", diffs));
        return toResponse(saved);
    }

    @Transactional
    public void deletePolicy(UUID orgId, UUID policyId, UUID userId) {
        SlaPolicy policy = slaPolicyRepository.findByIdAndOrgId(policyId, orgId)
                .orElseThrow(() -> new NotFoundException("SLA policy not found"));
        notifyPolicyChange(policy, userId, "deleted", "");
        policy.setDeletedAt(java.time.OffsetDateTime.now());
        slaPolicyRepository.save(policy);
    }

    /**
     * Notify the people affected by a policy change: assignees of the live SLA
     * instances governed by the policy, plus members of any team referenced by
     * its escalation tiers. Preference-aware (in-app + email) via the standard
     * notification service.
     */
    private void notifyPolicyChange(SlaPolicy policy, UUID actorUserId, String action, String changeSummary) {
        Map<UUID, Integer> openByOwner = new HashMap<>();
        for (SlaInstance si : slaInstanceRepository.findByPolicy_IdAndResolutionMetAtIsNull(policy.getId())) {
            for (AppUser owner : instanceOwners(si)) {
                openByOwner.merge(owner.getId(), 1, Integer::sum);
            }
        }
        Set<UUID> recipients = new HashSet<>(openByOwner.keySet());
        for (SlaEscalationTier tier : escalationTierRepository.findByPolicyIdOrderByLevelAsc(policy.getId())) {
            if (tier.getReassignToTeamId() != null) {
                teamMemberRepository.findByTeamId(tier.getReassignToTeamId())
                        .forEach(tm -> recipients.add(tm.getUserId()));
            }
        }
        recipients.remove(actorUserId);
        if (recipients.isEmpty()) {
            return;
        }

        String actorName = appUserRepository.findById(actorUserId)
                .map(AppUser::getDisplayName).orElse("An administrator");
        String subject = "SLA policy '" + policy.getName() + "' " + action;

        for (UUID recipientId : recipients) {
            int owned = openByOwner.getOrDefault(recipientId, 0);
            StringBuilder body = new StringBuilder("Hi,\n\n")
                    .append("SLA policy '").append(policy.getName()).append("' was ").append(action)
                    .append(" by ").append(actorName);
            if (!changeSummary.isBlank()) {
                body.append(": ").append(changeSummary);
            }
            body.append(".");
            if (owned > 0) {
                body.append("\n\n").append(owned)
                        .append(" open ticket(s) you own are governed by this policy.");
            } else {
                body.append("\n\nA team you belong to is referenced by this policy's escalation tiers.");
            }
            body.append("\n\nView SLA details:\n{{appBaseUrl}}/dashboard/sla-details");

            Map<String, Object> payload = new HashMap<>();
            payload.put("subject", subject);
            payload.put("body", body.toString());
            var content = notificationTemplateBuilder.forEvent("SLA_POLICY_CHANGED", payload);
            notificationService.send(new NotificationRequest(
                    policy.getOrgId(), recipientId, "SLA_POLICY_CHANGED",
                    content.inAppSubject(), content.inAppBody(),
                    "SLA_POLICY", policy.getId(), Notification.Channel.BOTH, content));
        }
    }

    private List<AppUser> instanceOwners(SlaInstance si) {
        try {
            if (si.getIncident() != null) {
                AppUser a = si.getIncident().getAssignee();
                return a != null ? List.of(a) : List.of();
            }
            if (si.getProblem() != null) {
                AppUser a = si.getProblem().getAssignee();
                return a != null ? List.of(a) : List.of();
            }
            if (si.getChangeRequest() != null) {
                AppUser a = si.getChangeRequest().getAssignee();
                return a != null ? List.of(a) : List.of();
            }
            if (si.getServiceRequest() != null) {
                return fulfillmentTaskRepository
                        .findByServiceRequestIdOrderBySequenceOrderAsc(si.getServiceRequest().getId())
                        .stream()
                        .map(FulfillmentTask::getAssignee)
                        .filter(Objects::nonNull)
                        .toList();
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            // Linked ticket is soft-deleted — no owner to notify.
        }
        return List.of();
    }

    private static String fmtMinutes(Integer minutes) {
        if (minutes == null) return "none";
        if (minutes % 60 == 0) return (minutes / 60) + "h";
        return minutes + "m";
    }

    /** workflowType is only meaningful on REQUEST policies; whitelist values. */
    private static String normalizeWorkflowType(SlaPolicyRequest request) {
        String wt = request.workflowType();
        if (wt == null || wt.isBlank()) {
            return null;
        }
        if (request.appliesTo() != SlaPolicy.AppliesTo.REQUEST) {
            throw new IllegalStateException("workflowType applies only to REQUEST policies");
        }
        return switch (wt) {
            case "FULL", "SOFTWARE", "INSTANT" -> wt;
            default -> throw new IllegalStateException("workflowType must be FULL, SOFTWARE, or INSTANT");
        };
    }

    private static String orAll(String value) {
        return value == null || value.isBlank() ? "all" : value;
    }

    /**
     * Agent view: the SLA policies that govern the caller's own work — all
     * incident/problem/change policies, plus request policies when the caller
     * is a fulfillment-task member. Includes target minutes, calendar and
     * escalation-tier configuration.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> mySlaTargets(AppUser user) {
        boolean isFulfiller = !fulfillmentTaskRepository
                .findByAssignee_IdAndDeletedAtIsNull(user.getId()).isEmpty();
        return slaPolicyRepository.findByOrgId(user.getOrgId()).stream()
                .filter(p -> p.getDeletedAt() == null)
                .filter(p -> p.getAppliesTo() != SlaPolicy.AppliesTo.REQUEST || isFulfiller)
                .map(this::toTargetMap)
                .toList();
    }

    private Map<String, Object> toTargetMap(SlaPolicy p) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", p.getId());
        row.put("name", p.getName());
        row.put("appliesTo", p.getAppliesTo() != null ? p.getAppliesTo().name() : null);
        row.put("priorityFilter", p.getPriorityFilter());
        row.put("workflowType", p.getWorkflowType());
        row.put("responseTargetMinutes", p.getResponseTargetMinutes());
        row.put("resolutionTargetMinutes", p.getResolutionTargetMinutes());
        BusinessCalendar cal = p.getBusinessHoursCalendar();
        row.put("calendar", cal == null ? null : Map.of(
                "id", cal.getId(),
                "name", cal.getName(),
                "timezone", cal.getTimezone() != null ? cal.getTimezone() : "",
                "workingHours", cal.getWorkingHours() != null ? cal.getWorkingHours() : "",
                "holidays", cal.getHolidays() != null ? cal.getHolidays() : ""));
        row.put("escalationTiers", escalationTierRepository
                .findByPolicyIdOrderByLevelAsc(p.getId())
                .stream()
                .map(t -> {
                    Map<String, Object> tier = new HashMap<>();
                    tier.put("level", t.getLevel());
                    tier.put("triggerType", t.getTriggerType() != null ? t.getTriggerType().name() : null);
                    tier.put("stuckStatus", t.getStuckStatus());
                    tier.put("stuckMinutes", t.getStuckMinutes());
                    tier.put("notifyRole", t.getNotifyRole());
                    tier.put("reassignToTeamId", t.getReassignToTeamId());
                    return tier;
                })
                .toList());
        return row;
    }

    @Transactional(readOnly = true)
    public List<BusinessCalendar> listCalendars(UUID orgId) {
        return businessCalendarRepository.findByOrgId(orgId);
    }

    @Transactional
    public BusinessCalendar createCalendar(UUID orgId, UUID userId, BusinessCalendarRequest request) {
        BusinessCalendar calendar = new BusinessCalendar();
        calendar.setOrgId(orgId);
        calendar.setName(request.name());
        calendar.setTimezone(request.timezone());
        calendar.setWorkingHours(request.workingHours());
        calendar.setHolidays(request.holidays());
        calendar.setCreatedBy(userId);
        calendar.setUpdatedBy(userId);
        return businessCalendarRepository.save(calendar);
    }

    @Transactional
    public BusinessCalendar updateCalendar(UUID orgId, UUID calendarId, UUID userId, BusinessCalendarRequest request) {
        BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(calendarId, orgId)
                .orElseThrow(() -> new NotFoundException("Business calendar not found"));
        calendar.setName(request.name());
        calendar.setTimezone(request.timezone());
        calendar.setWorkingHours(request.workingHours());
        calendar.setHolidays(request.holidays());
        calendar.setUpdatedBy(userId);
        return businessCalendarRepository.save(calendar);
    }

    @Transactional
    public void deleteCalendar(UUID orgId, UUID calendarId) {
        BusinessCalendar calendar = businessCalendarRepository.findByIdAndOrgId(calendarId, orgId)
                .orElseThrow(() -> new NotFoundException("Business calendar not found"));
        calendar.setDeletedAt(java.time.OffsetDateTime.now());
        businessCalendarRepository.save(calendar);
    }
}
