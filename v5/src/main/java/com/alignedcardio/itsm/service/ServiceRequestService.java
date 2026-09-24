package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.servicerequest.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.entity.Priority;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.event.ServiceRequestEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.CatalogItemRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.LocationRepository;
import com.alignedcardio.itsm.repository.PriorityRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.alignedcardio.itsm.repository.TeamMemberRepository;
import com.alignedcardio.itsm.util.DateFormats;
import com.alignedcardio.itsm.util.PhoneNumbers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceRequestService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceRequestService.class);

    private final ServiceRequestRepository serviceRequestRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;
    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationService notificationService;
    private final NotificationTemplateBuilder notificationTemplateBuilder;
    private final SlaEngine slaEngine;
    private final PriorityRepository priorityRepository;

    /** IT Fulfillment team — fulfiller assignees must be members (seeded in V31). */
    private static final UUID IT_FULFILLMENT_TEAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    public ServiceRequestService(ServiceRequestRepository serviceRequestRepository,
                                 CatalogItemRepository catalogItemRepository,
                                 FulfillmentTaskRepository fulfillmentTaskRepository,
                                 AppUserRepository appUserRepository,
                                 LocationRepository locationRepository,
                                 FormSchemaValidator formSchemaValidator,
                                 ObjectMapper objectMapper,
                                 EntityManager entityManager,
                                 AuditLogRepository auditLogRepository,
                                 ApplicationEventPublisher eventPublisher,
                                 TeamMemberRepository teamMemberRepository,
                                 NotificationService notificationService,
                                 NotificationTemplateBuilder notificationTemplateBuilder,
                                 SlaEngine slaEngine,
                                 PriorityRepository priorityRepository) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
        this.formSchemaValidator = formSchemaValidator;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.teamMemberRepository = teamMemberRepository;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
        this.slaEngine = slaEngine;
        this.priorityRepository = priorityRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> list(UUID orgId, boolean showDeleted) {
        return serviceRequestRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .filter(sr -> showDeleted ? sr.getDeletedAt() != null : sr.getDeletedAt() == null)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyTaskResponse> myTasks(AppUser user) {
        return fulfillmentTaskRepository
                .findByAssignee_IdAndStatusInOrderByServiceRequest_CreatedAtDesc(
                        user.getId(),
                        List.of(FulfillmentTask.Status.PENDING,
                                FulfillmentTask.Status.ORDERED,
                                FulfillmentTask.Status.DELIVERY_DATE_SET,
                                FulfillmentTask.Status.DELIVERED))
                .stream()
                .map(t -> new MyTaskResponse(
                        t.getId(),
                        t.getDescription(),
                        t.getSequenceOrder(),
                        t.getStatus(),
                        t.getExpectedDeliveryDate(),
                        t.getServiceRequest().getId(),
                        t.getServiceRequest().getNumber(),
                        t.getServiceRequest().getCatalogItem() != null
                                ? t.getServiceRequest().getCatalogItem().getName() : null,
                        t.getServiceRequest().getRequester() != null
                                ? t.getServiceRequest().getRequester().getDisplayName() : null,
                        t.getAssignedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> listMine(AppUser user) {
        return serviceRequestRepository
                .findByOrgIdAndRequester_IdOrderByCreatedAtDesc(user.getOrgId(), user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // Service requests the agent was previously assigned to fulfill (audit
    // TASK_ASSIGNED history), even if the task is now completed/reassigned.
    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> recentlyWorkedOn(AppUser user) {
        Set<UUID> ids = auditLogRepository
                .findServiceRequestAssigneeHistory(user.getOrgId(), user.getId().toString())
                .stream()
                .map(AuditLog::getEntityId)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return serviceRequestRepository
                .findByOrgIdAndIdInOrderByCreatedAtDesc(user.getOrgId(), ids)
                .stream()
                .filter(sr -> sr.getDeletedAt() == null)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceRequestResponse create(AppUser user, UUID orgId, ServiceRequestCreateRequest request) {
        CatalogItem item = catalogItemRepository.findByOrgIdAndId(orgId, request.catalogItemId())
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));

        formSchemaValidator.validate(item.getFormSchema().toString(), request.formData());

        ServiceRequest sr = new ServiceRequest();
        sr.setOrgId(orgId);
        sr.setNumber(generateServiceRequestNumber());
        sr.setCatalogItem(item);
        sr.setRequester(user);
        try {
            sr.setFormData(objectMapper.readTree(request.formData()));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid form data JSON", e);
        }
        sr.setNeededBy(request.neededBy());
        sr.setPhone(PhoneNumbers.normalize(request.phone()));
        if (request.locationId() == null) {
            throw new IllegalStateException("Location is required for every service request");
        }
        Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                .orElseThrow(() -> new NotFoundException("Location not found"));
        sr.setLocation(location);
        if (request.priorityId() != null) {
            sr.setPriority(priorityRepository.findByOrgIdAndId(orgId, request.priorityId())
                    .orElseThrow(() -> new NotFoundException("Priority not found")));
        }
        sr.setApprovalRequired(computeApprovalRequired(item, sr.getFormData()));
        sr.setCreatedBy(user.getId());
        sr.setUpdatedBy(user.getId());

        ServiceRequest saved = serviceRequestRepository.save(sr);
        entityManager.flush();
        entityManager.refresh(saved);

        // Start the service-request SLA clock as soon as the request is created.
        slaEngine.onServiceRequestCreated(saved);

        publishEvent(saved, "SUBMITTED");
        return submit(user, orgId, saved.getId());
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse get(AppUser user, UUID orgId, UUID id) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        requireVisibleTo(user, sr);
        return toResponse(sr);
    }

    // Non-staff callers may only see requests they submitted or are assigned to
    // approve. 404 (not 403) so existence isn't leaked to other users.
    private void requireVisibleTo(AppUser user, ServiceRequest sr) {
        if (isStaff(user)) {
            return;
        }
        boolean isRequester = sr.getRequester() != null && sr.getRequester().getId().equals(user.getId());
        boolean isApprover = sr.getApprover() != null && sr.getApprover().getId().equals(user.getId());
        if (!isRequester && !isApprover) {
            throw new NotFoundException("Service request not found");
        }
    }

    private boolean isStaff(AppUser user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(name -> "AGENT".equals(name) || "TEAM_LEAD".equals(name)
                        || "ADMIN".equals(name) || "SUPER_ADMIN".equals(name));
    }

    @Transactional
    public ServiceRequestResponse submit(AppUser user, UUID orgId, UUID id) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        if (sr.getStatus() != ServiceRequest.Status.SUBMITTED) {
            throw new IllegalStateException("Only SUBMITTED requests can be submitted");
        }

        ServiceRequest.Status target = sr.isApprovalRequired() ? ServiceRequest.Status.PENDING_APPROVAL : ServiceRequest.Status.IN_FULFILLMENT;
        ServiceRequestStatusMachine.validate(sr, target != ServiceRequest.Status.FULFILLED, target);

        recordActivity(sr, user.getId(), "SUBMITTED", null,
                Map.of("status", target.name(), "number", sr.getNumber()));

        if (target == ServiceRequest.Status.PENDING_APPROVAL) {
            AppUser approver = resolveApprover(sr);
            sr.setApprover(approver);
            recordActivity(sr, user.getId(), "ROUTED_TO_APPROVER", null,
                    Map.of("approverId", approver.getId(), "approverName", approver.getDisplayName()));
        }

        sr.setStatus(target);

        if (target == ServiceRequest.Status.IN_FULFILLMENT) {
            seedFulfillmentTasks(user, sr);
        }

        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Pause (PENDING_APPROVAL) or start/resume (IN_FULFILLMENT) the SLA clock.
        slaEngine.onServiceRequestStatusChanged(saved);

        publishEvent(saved, target.name());
        return toResponse(saved);
    }

    @Transactional
    public ServiceRequestResponse sendToApproval(AppUser user, UUID orgId, UUID id, String reason) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        if (sr.getStatus() == ServiceRequest.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("Request is already pending approval");
        }
        requireNotTerminal(sr);

        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A reason is required when sending a request to approval");
        }

        AppUser approver = resolveApprover(sr);

        sr.setPreviousStatus(sr.getStatus());
        sr.setApprovalRequired(true);
        sr.setApprovalDecision(ServiceRequest.ApprovalDecision.PENDING);
        sr.setStatus(ServiceRequest.Status.PENDING_APPROVAL);
        sr.setApprover(approver);
        sr.setApprovalComment(null);
        sr.setDecidedAt(null);
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        recordActivity(sr, user.getId(), "SENT_TO_APPROVAL",
                Map.of("status", sr.getPreviousStatus().name(),
                        "approverId", approver.getId(),
                        "approverName", approver.getDisplayName()),
                Map.of("status", sr.getStatus().name(),
                        "approverId", approver.getId(),
                        "approverName", approver.getDisplayName(),
                        "reason", reason,
                        "sentBy", user.getDisplayName()));

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Pause the SLA clock while pending the new approval.
        slaEngine.onServiceRequestStatusChanged(saved);

        notifyApproverOfRetroactiveApproval(user, saved, reason);

        publishEvent(saved, "PENDING_APPROVAL");
        return toResponse(saved);
    }

    private boolean computeApprovalRequired(CatalogItem item, JsonNode formData) {
        if (item.getFormSchema() == null || !item.getFormSchema().isArray()) {
            return false;
        }
        for (JsonNode field : item.getFormSchema()) {
            String type = field.has("type") ? field.get("type").asText() : "string";
            if (!("select".equals(type) || "select_with_other".equals(type))) {
                continue;
            }
            String name = field.get("name").asText();
            JsonNode valueNode = formData.get(name);
            if (valueNode == null || valueNode.isNull() || !valueNode.isTextual()) {
                continue;
            }
            String selected = valueNode.asText();
            if (selected.isBlank()) {
                continue;
            }
            boolean matchedPreset = false;
            if (field.hasNonNull("options")) {
                for (JsonNode opt : field.get("options")) {
                    String optionValue = null;
                    boolean optionRequiresApproval = false;
                    if (opt.isTextual()) {
                        optionValue = opt.asText();
                    } else if (opt.isObject() && opt.hasNonNull("value")) {
                        optionValue = opt.get("value").asText();
                        optionRequiresApproval = opt.hasNonNull("requiresApproval")
                                && opt.get("requiresApproval").asBoolean();
                    }
                    if (selected.equals(optionValue)) {
                        matchedPreset = true;
                        if (optionRequiresApproval) {
                            return true;
                        }
                        break;
                    }
                }
            }
            if (!matchedPreset && "select_with_other".equals(type)) {
                boolean otherRequiresApproval = field.hasNonNull("otherRequiresApproval")
                        && field.get("otherRequiresApproval").asBoolean();
                if (otherRequiresApproval) {
                    return true;
                }
            }
        }
        return false;
    }

    private AppUser resolveApprover(ServiceRequest sr) {
        Location location = sr.getLocation();
        if (location != null && location.getApprovalManager() != null) {
            return location.getApprovalManager();
        }
        CatalogItem item = sr.getCatalogItem();
        if (item.getApprover() != null) {
            return item.getApprover();
        }
        throw new IllegalStateException(
                "Catalog item '" + item.getName() + "' requires approval but no approver could be resolved: "
                        + "the request's location has no approval manager and the catalog item has no approver. "
                        + "Set an approval manager on the location or an approver on the catalog item.");
    }

    @Transactional
    public ServiceRequestResponse decide(AppUser user, UUID orgId, UUID id, ApprovalRequest request) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        if (sr.getStatus() != ServiceRequest.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("Request is not pending approval");
        }

        if (sr.getApprover() != null && !sr.getApprover().getId().equals(user.getId())) {
            throw new IllegalStateException("You are not the designated approver for this request");
        }

        if (request.comment() == null || request.comment().isBlank()) {
            throw new IllegalStateException("A comment is required when approving or rejecting a request");
        }

        sr.setApprover(user);
        sr.setApprovalComment(request.comment());
        sr.setDecidedAt(OffsetDateTime.now());
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        boolean retroactive = sr.getPreviousStatus() != null;
        if (request.approve()) {
            sr.setApprovalDecision(ServiceRequest.ApprovalDecision.APPROVED);
            sr.setStatus(ServiceRequest.Status.APPROVED);
            sr.setPreviousStatus(null);
        } else {
            sr.setApprovalDecision(ServiceRequest.ApprovalDecision.REJECTED);
            sr.setStatus(retroactive ? ServiceRequest.Status.REJECTED_NEEDS_REVIEW : ServiceRequest.Status.REJECTED);
        }

        recordActivity(sr, user.getId(), sr.getApprovalDecision().name(),
                Map.of("status", "PENDING_APPROVAL"),
                Map.of("status", sr.getStatus().name(),
                        "decidedBy", user.getDisplayName(),
                        "comment", request.comment() != null ? request.comment() : ""));

        if (sr.getStatus() == ServiceRequest.Status.APPROVED) {
            seedFulfillmentTasks(user, sr);
        }

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Resume (APPROVED) or stop (REJECTED/REJECTED_NEEDS_REVIEW) the SLA clock.
        slaEngine.onServiceRequestStatusChanged(saved);

        publishEvent(saved, sr.getApprovalDecision().name());
        if (sr.getStatus() == ServiceRequest.Status.APPROVED) {
            publishEvent(saved, "IN_FULFILLMENT");
            // Broadcast to org admins when an approved request needs a fulfiller.
            // This is a broadcast-at-scale tradeoff for small orgs; revisit if org size grows.
            notifyAdminsForFulfillerAssignment(saved);
        }

        if (retroactive && !request.approve()) {
            notifyAdminsOfRetroactiveRejection(saved, user, request.comment());
        }

        return toResponse(saved);
    }

    /**
     * SUPER_ADMIN record correction. Editable: location, priority, phone,
     * needed-by, form data. Edits never touch status or approval state — a
     * routed approval must still be decided or bypassed explicitly. The one
     * exception: changing the location while the request is still
     * PENDING_APPROVAL re-resolves the designated approver so the pending
     * item lands with the right approval manager.
     */
    @Transactional
    public ServiceRequestResponse update(AppUser user, UUID orgId, UUID id, ServiceRequestUpdateRequest request) {
        if (!isSuperAdmin(user)) {
            throw new IllegalStateException("Only SUPER_ADMIN can edit service requests");
        }
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean priorityChanged = false;

        if (request.locationId() != null) {
            Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                    .orElseThrow(() -> new NotFoundException("Location not found"));
            if (sr.getLocation() == null || !sr.getLocation().getId().equals(location.getId())) {
                before.put("location", sr.getLocation() != null ? sr.getLocation().getName() : null);
                after.put("location", location.getName());
                sr.setLocation(location);
                if (sr.getStatus() == ServiceRequest.Status.PENDING_APPROVAL) {
                    AppUser newApprover = resolveApprover(sr);
                    AppUser oldApprover = sr.getApprover();
                    if (oldApprover == null || !oldApprover.getId().equals(newApprover.getId())) {
                        before.put("approver", oldApprover != null ? oldApprover.getDisplayName() : null);
                        after.put("approver", newApprover.getDisplayName());
                        sr.setApprover(newApprover);
                    }
                }
            }
        }
        if (request.priorityId() != null) {
            Priority priority = priorityRepository.findByOrgIdAndId(orgId, request.priorityId())
                    .orElseThrow(() -> new NotFoundException("Priority not found"));
            if (sr.getPriority() == null || !sr.getPriority().getId().equals(priority.getId())) {
                before.put("priority", sr.getPriority() != null ? sr.getPriority().getName() : null);
                after.put("priority", priority.getName());
                sr.setPriority(priority);
                priorityChanged = true;
            }
        }
        if (request.phone() != null) {
            String phone = PhoneNumbers.normalize(request.phone());
            if (!java.util.Objects.equals(sr.getPhone(), phone)) {
                before.put("phone", sr.getPhone());
                after.put("phone", phone);
                sr.setPhone(phone);
            }
        }
        if (request.neededBy() != null
                && !request.neededBy().equals(sr.getNeededBy())) {
            before.put("neededBy", sr.getNeededBy() != null ? sr.getNeededBy().toString() : null);
            after.put("neededBy", request.neededBy().toString());
            sr.setNeededBy(request.neededBy());
        }
        if (request.formData() != null) {
            formSchemaValidator.validate(sr.getCatalogItem().getFormSchema().toString(), request.formData());
            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(request.formData());
            } catch (Exception e) {
                throw new IllegalStateException("Invalid form data JSON", e);
            }
            if (!parsed.equals(sr.getFormData())) {
                before.put("formData", sr.getFormData());
                after.put("formData", parsed);
                sr.setFormData(parsed);
            }
        }

        if (before.isEmpty()) {
            return toResponse(sr);
        }

        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());
        recordActivity(sr, user.getId(), "EDITED", before, after);
        ServiceRequest saved = serviceRequestRepository.save(sr);
        // A priority change re-matches the SLA policy and re-targets unmet
        // clocks — previously the Edit path updated the field but left the
        // SLA untouched.
        if (priorityChanged) {
            slaEngine.onServiceRequestPriorityChanged(saved);
        }
        return toResponse(saved);
    }

    /**
     * SUPER_ADMIN override: approves a PENDING_APPROVAL request without the
     * designated approver's decision. The record stays honest — `approver`
     * remains the routed manager, `bypassedBy` names who actually decided,
     * and the request never appears in the manager's "approved by me" list.
     */
    @Transactional
    public ServiceRequestResponse bypassApproval(AppUser user, UUID orgId, UUID id, String reason) {
        if (!isSuperAdmin(user)) {
            throw new IllegalStateException("Only SUPER_ADMIN can bypass approval");
        }
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        if (sr.getStatus() != ServiceRequest.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("Request is not pending approval");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A reason is required when bypassing approval");
        }

        AppUser designatedApprover = sr.getApprover();

        sr.setApprovalDecision(ServiceRequest.ApprovalDecision.APPROVED);
        sr.setApprovalComment(reason);
        sr.setApprovalBypassed(true);
        sr.setBypassedBy(user);
        sr.setBypassReason(reason);
        sr.setDecidedAt(OffsetDateTime.now());
        sr.setPreviousStatus(null);
        sr.setStatus(ServiceRequest.Status.APPROVED);
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        recordActivity(sr, user.getId(), "APPROVAL_BYPASSED",
                Map.of("status", "PENDING_APPROVAL",
                        "approver", designatedApprover != null ? designatedApprover.getDisplayName() : ""),
                Map.of("status", sr.getStatus().name(),
                        "bypassedBy", user.getDisplayName(),
                        "originalApprover", designatedApprover != null ? designatedApprover.getDisplayName() : "",
                        "reason", reason));

        seedFulfillmentTasks(user, sr);
        ServiceRequest saved = serviceRequestRepository.save(sr);

        slaEngine.onServiceRequestStatusChanged(saved);

        publishEvent(saved, "APPROVED");
        publishEvent(saved, "IN_FULFILLMENT");
        notifyAdminsForFulfillerAssignment(saved);
        if (designatedApprover != null) {
            notifyApproverOfBypass(user, saved, designatedApprover, reason);
        }

        return toResponse(saved);
    }

    /** Tell the routed approver their pending item was overridden by an admin. */
    private void notifyApproverOfBypass(AppUser admin, ServiceRequest sr, AppUser approver, String reason) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterName", sr.getRequester().getDisplayName());
        payload.put("actorName", admin.getDisplayName());
        payload.put("reason", reason);
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());
        payload.put("subject", "Request #" + sr.getNumber() + " was approved by admin override");
        payload.put("body", "Request #" + sr.getNumber() + " (" + sr.getCatalogItem().getName()
                + ") for " + sr.getRequester().getDisplayName()
                + " was awaiting your approval. " + admin.getDisplayName()
                + " bypassed your pending approval and approved it directly.\n\nReason: " + reason);
        try {
            var content = notificationTemplateBuilder.forEvent("SR_APPROVAL_BYPASSED", payload);
            notificationService.send(new NotificationRequest(
                    sr.getOrgId(),
                    approver.getId(),
                    "SR_APPROVAL_BYPASSED",
                    content.inAppSubject(),
                    content.inAppBody(),
                    "SERVICE_REQUEST",
                    sr.getId(),
                    null,
                    content));
        } catch (Exception e) {
            logger.warn("Failed to send SR_APPROVAL_BYPASSED to {}", approver.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> listPendingApprovals(AppUser user) {
        return serviceRequestRepository
                .findByOrgIdAndStatusAndApprover_IdOrderByCreatedAtDesc(
                        user.getOrgId(), ServiceRequest.Status.PENDING_APPROVAL, user.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> listApprovedByMe(AppUser user) {
        return serviceRequestRepository
                .findByOrgIdAndApprover_IdAndApprovalDecisionAndApprovalBypassedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(
                        user.getOrgId(), user.getId(), ServiceRequest.ApprovalDecision.APPROVED)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestActivityResponse> getActivity(AppUser user, UUID orgId, UUID id) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        requireVisibleTo(user, sr);
        return auditLogRepository
                .findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(orgId, "SERVICE_REQUEST", id)
                .stream()
                .map(log -> new ServiceRequestActivityResponse(
                        log.getId(),
                        log.getAction(),
                        log.getActorUserId(),
                        log.getActorUserId() != null
                                ? appUserRepository.findById(log.getActorUserId()).map(AppUser::getDisplayName).orElse(null)
                                : null,
                        log.getBeforeState(),
                        log.getAfterState(),
                        log.getCreatedAt()))
                .toList();
    }

    private void recordActivity(ServiceRequest sr, UUID actorId, String action,
                                Map<String, ?> before, Map<String, ?> after) {
        try {
            AuditLog log = new AuditLog();
            log.setOrgId(sr.getOrgId());
            log.setActorUserId(actorId);
            log.setAction(action);
            log.setEntityType("SERVICE_REQUEST");
            log.setEntityId(sr.getId());
            log.setBeforeState(before != null ? objectMapper.writeValueAsString(before) : null);
            log.setAfterState(after != null ? objectMapper.writeValueAsString(after) : null);
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write service request audit log for {}", sr.getId(), e);
        }
    }

    private void publishEvent(ServiceRequest sr, String triggerType) {
        publishEvent(sr, triggerType, null);
    }

    private void publishEvent(ServiceRequest sr, String triggerType, AppUser actor) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("status", sr.getStatus().name());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("requesterName", sr.getRequester().getDisplayName());
        payload.put("requesterFirstName", firstName(sr.getRequester().getDisplayName()));
        payload.put("locationName", sr.getLocation() != null ? sr.getLocation().getName() : "");
        if (sr.getApprover() != null) {
            payload.put("approverId", sr.getApprover().getId());
            payload.put("approverName", sr.getApprover().getDisplayName());
            payload.put("approverFirstName", firstName(sr.getApprover().getDisplayName()));
        }
        if (sr.getApprovalComment() != null) {
            payload.put("reason", sr.getApprovalComment());
            payload.put("rejectionReason", sr.getApprovalComment());
            payload.put("approvalComment", sr.getApprovalComment());
        }
        if (sr.getDecidedAt() != null) {
            payload.put("approvedDate", DateFormats.formatDateTime(sr.getDecidedAt()));
        }
        if (actor != null) {
            payload.put("fulfillerName", actor.getDisplayName());
        }
        eventPublisher.publishEvent(new ServiceRequestEvent(sr.getOrgId(), sr.getId(), triggerType, payload));
    }

    private void notifyAdminsForFulfillerAssignment(ServiceRequest sr) {
        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                sr.getOrgId(), List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("number", sr.getNumber());
            payload.put("catalogItemName", sr.getCatalogItem().getName());
            payload.put("approvedDate", sr.getDecidedAt() != null ? DateFormats.formatDateTime(sr.getDecidedAt()) : "");
            payload.put("entityType", "SERVICE_REQUEST");
            payload.put("entityId", sr.getId());
            try {
                var content = notificationTemplateBuilder.forEvent("SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT", payload);
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        admin.getId(),
                        "SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT to {}", admin.getId(), e);
            }
        }
    }

    private String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "there";
        return displayName.trim().split("\\s+")[0];
    }

    private void notifyApproverOfRetroactiveApproval(AppUser sender, ServiceRequest sr, String reason) {
        if (sr.getApprover() == null) return;
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterName", sr.getRequester().getDisplayName());
        payload.put("locationName", sr.getLocation() != null ? sr.getLocation().getName() : "");
        payload.put("actorName", sender.getDisplayName());
        payload.put("reason", reason);
        payload.put("approverFirstName", firstName(sr.getApprover().getDisplayName()));
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());
        try {
            var content = notificationTemplateBuilder.forEvent("SR_SENT_TO_APPROVAL", payload);
            notificationService.send(new NotificationRequest(
                    sr.getOrgId(),
                    sr.getApprover().getId(),
                    "SR_SENT_TO_APPROVAL",
                    content.inAppSubject(),
                    content.inAppBody(),
                    "SERVICE_REQUEST",
                    sr.getId(),
                    null,
                    content));
        } catch (Exception e) {
            logger.warn("Failed to send SR_SENT_TO_APPROVAL to {}", sr.getApprover().getId(), e);
        }
    }

    private void notifyAdminsOfRetroactiveRejection(ServiceRequest sr, AppUser approver, String comment) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());
        payload.put("approverName", approver.getDisplayName());
        payload.put("requesterFirstName", firstName(sr.getRequester().getDisplayName()));
        payload.put("comment", comment != null ? comment : "");

        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                sr.getOrgId(), List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            try {
                var content = notificationTemplateBuilder.forEvent("SR_RETROACTIVE_APPROVAL_REJECTED", payload);
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        admin.getId(),
                        "SR_RETROACTIVE_APPROVAL_REJECTED",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send SR_RETROACTIVE_APPROVAL_REJECTED to {}", admin.getId(), e);
            }
        }

        try {
            var requesterContent = notificationTemplateBuilder.forEvent("SR_RETROACTIVE_APPROVAL_REJECTED", payload);
            notificationService.send(new NotificationRequest(
                    sr.getOrgId(),
                    sr.getRequester().getId(),
                    "SR_RETROACTIVE_APPROVAL_REJECTED",
                    requesterContent.inAppSubject(),
                    requesterContent.inAppBody(),
                    "SERVICE_REQUEST",
                    sr.getId(),
                    null,
                    requesterContent));
        } catch (Exception e) {
            logger.warn("Failed to send SR_RETROACTIVE_APPROVAL_REJECTED to requester {}", sr.getRequester().getId(), e);
        }
    }

    @Transactional
    public ServiceRequestResponse updateStatus(AppUser user, UUID orgId, UUID id, ServiceRequest.Status newStatus) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        // Cancellation always requires a reason — it must go through the
        // dedicated /cancel endpoint so the audit trail captures it.
        if (newStatus == ServiceRequest.Status.CANCELLED) {
            throw new IllegalStateException("Cancelling a request requires a reason — use the cancel action");
        }

        boolean allCompleted = areAllTasksCompleted(sr);
        ServiceRequestStatusMachine.validate(sr, allCompleted, newStatus);
        sr.setStatus(newStatus);
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Update the service-request SLA clock for pause/resume/stop.
        slaEngine.onServiceRequestStatusChanged(saved);

        publishEvent(saved, newStatus.name());
        return toResponse(saved);
    }

    /**
     * Cancel a request — distinct from a rejection (which is an approver's
     * decision). The requester may cancel their own request; staff may cancel
     * any. Reason is mandatory and goes on the audit trail. The designated
     * approver (if still pending) and assigned fulfillers are notified so a
     * pending item doesn't silently disappear from anyone's queue.
     */
    @Transactional
    public ServiceRequestResponse cancel(AppUser user, UUID orgId, UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalStateException("A reason is required to cancel a request");
        }
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        boolean isRequester = sr.getRequester() != null
                && sr.getRequester().getId().equals(user.getId());
        if (!isRequester && !isStaff(user)) {
            throw new IllegalStateException("Only the requester or IT staff can cancel a request");
        }

        ServiceRequest.Status previous = sr.getStatus();
        ServiceRequestStatusMachine.validate(sr, areAllTasksCompleted(sr), ServiceRequest.Status.CANCELLED);
        sr.setStatus(ServiceRequest.Status.CANCELLED);
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());
        ServiceRequest saved = serviceRequestRepository.save(sr);

        slaEngine.onServiceRequestStatusChanged(saved);
        recordActivity(saved, user.getId(), "CANCELLED",
                Map.of("status", previous.name()),
                Map.of("status", "CANCELLED", "reason", reason.trim()));
        publishEvent(saved, "CANCELLED");
        notifyCancellation(saved, user, reason.trim(), previous);
        return toResponse(saved);
    }

    private void notifyCancellation(ServiceRequest sr, AppUser actor, String reason,
                                    ServiceRequest.Status previousStatus) {
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        notified.add(actor.getId()); // the canceller already knows

        List<AppUser> recipients = new ArrayList<>();
        if (sr.getRequester() != null) recipients.add(sr.getRequester());
        if (previousStatus == ServiceRequest.Status.PENDING_APPROVAL && sr.getApprover() != null) {
            recipients.add(sr.getApprover());
        }
        for (FulfillmentTask task : fulfillmentTaskRepository
                .findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId())) {
            if (task.getAssignee() != null && task.getDeletedAt() == null) {
                recipients.add(task.getAssignee());
            }
        }

        for (AppUser recipient : recipients) {
            if (!notified.add(recipient.getId())) continue;
            try {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("recipientFirstName", firstName(recipient.getDisplayName()));
                payload.put("number", sr.getNumber());
                payload.put("catalogItemName", sr.getCatalogItem() != null ? sr.getCatalogItem().getName() : "Service request");
                payload.put("actorName", actor.getDisplayName());
                payload.put("reason", reason);
                payload.put("entityType", "SERVICE_REQUEST");
                payload.put("entityId", sr.getId());
                var content = notificationTemplateBuilder.forEvent("SERVICE_REQUEST_CANCELLED", payload);
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        recipient.getId(),
                        "SERVICE_REQUEST_CANCELLED",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send cancellation notification to {}", recipient.getId(), e);
            }
        }
    }

    private boolean isSuperAdmin(AppUser user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch("SUPER_ADMIN"::equals);
    }

    private boolean isAdminOrSuperAdmin(AppUser user) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(name -> "ADMIN".equals(name) || "SUPER_ADMIN".equals(name));
    }

    /**
     * Task progression requires a live request: ON_HOLD freezes work until
     * resumed, and FULFILLED/CANCELLED are terminal — nothing can progress.
     */
    private void requireWorkable(ServiceRequest sr) {
        if (sr.getStatus() == ServiceRequest.Status.ON_HOLD) {
            throw new IllegalStateException(
                    "Request is on hold — resume it before progressing fulfillment tasks");
        }
        requireNotTerminal(sr);
    }

    /**
     * Terminal requests accept no fulfillment changes at all — including
     * reassignment, which remains available while merely ON_HOLD.
     */
    private void requireNotTerminal(ServiceRequest sr) {
        if (sr.getStatus() == ServiceRequest.Status.FULFILLED
                || sr.getStatus() == ServiceRequest.Status.CANCELLED) {
            throw new IllegalStateException(
                    "Request is " + sr.getStatus().name().toLowerCase()
                            + " — no further fulfillment changes are allowed");
        }
    }

    private boolean isTaskActor(AppUser user, FulfillmentTask task) {
        return isStaff(user)
                || (task.getAssignee() != null && task.getAssignee().getId().equals(user.getId()));
    }

    /**
     * SUPER_ADMIN picks a specific fulfiller from the IT Fulfillment team.
     * Assignability is team membership, not a role.
     */
    @Transactional
    public ServiceRequestResponse assignTask(AppUser user, UUID orgId, UUID requestId, UUID taskId, UUID assigneeId) {
        return assignTask(user, orgId, requestId, taskId, assigneeId, null);
    }

    /**
     * Assign a fulfiller to a task — optionally setting the request's priority
     * in the same action. Priority changes take the same path as the Edit
     * action: audit trail + SLA policy re-match.
     */
    @Transactional
    public ServiceRequestResponse assignTask(AppUser user, UUID orgId, UUID requestId, UUID taskId, UUID assigneeId, UUID priorityId) {
        if (!isSuperAdmin(user)) {
            throw new IllegalStateException("Only SUPER_ADMIN can assign fulfillment tasks");
        }
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }
        // Assignment stays available while ON_HOLD (administrative, not work
        // progression) but not on terminal requests.
        requireNotTerminal(sr);

        boolean isMember = teamMemberRepository.findByTeamId(IT_FULFILLMENT_TEAM_ID).stream()
                .anyMatch(tm -> tm.getUser() != null && tm.getUser().getId().equals(assigneeId));
        if (!isMember) {
            throw new IllegalStateException("Assignee must be a member of the IT Fulfillment team");
        }
        AppUser assignee = appUserRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        // Optional priority set at assignment time — same update logic and
        // audit trail as the Edit action.
        boolean priorityChanged = false;
        if (priorityId != null) {
            Priority priority = priorityRepository.findByOrgIdAndId(orgId, priorityId)
                    .orElseThrow(() -> new NotFoundException("Priority not found"));
            if (sr.getPriority() == null || !sr.getPriority().getId().equals(priority.getId())) {
                String beforeName = sr.getPriority() != null ? sr.getPriority().getName() : null;
                sr.setPriority(priority);
                priorityChanged = true;
                recordActivity(sr, user.getId(), "EDITED",
                        Map.of("priority", beforeName != null ? beforeName : ""),
                        Map.of("priority", priority.getName()));
            }
        }

        task.setAssignee(assignee);
        task.setAssignedBy(user);
        task.setAssignedAt(OffsetDateTime.now());
        // Status stays PENDING: the fulfiller explicitly marks the task ORDERED
        // as the first progressive step after assignment.
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);
        if (priorityChanged) {
            sr.setUpdatedBy(user.getId());
            sr.setUpdatedAt(OffsetDateTime.now());
            serviceRequestRepository.save(sr);
            slaEngine.onServiceRequestPriorityChanged(sr);
        }

        recordActivity(sr, user.getId(), "TASK_ASSIGNED", null,
                Map.of("taskId", task.getId(), "taskDescription", task.getDescription(),
                        "assigneeId", assignee.getId(), "assigneeName", assignee.getDisplayName()));

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        payload.put("assigneeId", assignee.getId());
        payload.put("assigneeName", assignee.getDisplayName());
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "TASK_ASSIGNED", payload));

        if (!assignee.getId().equals(user.getId())) {
            try {
                Map<String, Object> notificationPayload = new LinkedHashMap<>();
                notificationPayload.put("number", sr.getNumber());
                notificationPayload.put("catalogItemName", sr.getCatalogItem().getName());
                notificationPayload.put("taskDescription", task.getDescription());
                notificationPayload.put("actorName", user.getDisplayName());
                notificationPayload.put("assigneeFirstName", firstName(assignee.getDisplayName()));
                notificationPayload.put("entityType", "SERVICE_REQUEST");
                notificationPayload.put("entityId", sr.getId());
                var content = notificationTemplateBuilder.forEvent("FULFILLMENT_TASK_ASSIGNED", notificationPayload);
                notificationService.send(new NotificationRequest(
                        sr.getOrgId(),
                        assignee.getId(),
                        "FULFILLMENT_TASK_ASSIGNED",
                        content.inAppSubject(),
                        content.inAppBody(),
                        "SERVICE_REQUEST",
                        sr.getId(),
                        null,
                        content));
            } catch (Exception e) {
                logger.warn("Failed to send fulfillment task assignment notification to {}", assignee.getId(), e);
            }
        }

        return toResponse(sr);
    }

    /**
     * Progressive step 1: the assigned fulfiller (or staff) marks the task as
     * ordered. Only allowed while the task is PENDING and has an assignee.
     * Optional orderId and vendor are captured for the FULL (physical) workflow.
     */
    @Transactional
    public ServiceRequestResponse markOrdered(AppUser user, UUID orgId, UUID requestId, UUID taskId) {
        return markOrdered(user, orgId, requestId, taskId, null, null);
    }

    @Transactional
    public ServiceRequestResponse markOrdered(AppUser user, UUID orgId, UUID requestId, UUID taskId,
                                              String orderId, String vendor) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }
        requireWorkable(sr);
        if (!isTaskActor(user, task)) {
            throw new IllegalStateException("Only the assigned fulfiller or staff can mark this task ordered");
        }
        if (task.getStatus() != FulfillmentTask.Status.PENDING) {
            throw new IllegalStateException("Only PENDING tasks can be marked ordered");
        }
        if (task.getAssignee() == null) {
            throw new IllegalStateException("Assign a fulfiller before marking the task ordered");
        }

        task.setStatus(FulfillmentTask.Status.ORDERED);
        if (orderId != null && !orderId.isBlank()) {
            task.setOrderId(orderId.trim());
        }
        if (vendor != null && !vendor.isBlank()) {
            task.setVendor(vendor.trim());
        }
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

        Map<String, Object> after = new java.util.HashMap<>(Map.of(
                "taskId", task.getId(),
                "taskDescription", task.getDescription()));
        if (task.getOrderId() != null) after.put("orderId", task.getOrderId());
        if (task.getVendor() != null) after.put("vendor", task.getVendor());
        recordActivity(sr, user.getId(), "TASK_ORDERED", null, after);

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        if (task.getOrderId() != null) payload.put("orderId", task.getOrderId());
        if (task.getVendor() != null) payload.put("vendor", task.getVendor());
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "TASK_ORDERED", payload));

        return toResponse(sr);
    }

    @Transactional
    public ServiceRequestResponse completeTask(AppUser user, UUID orgId, UUID requestId, UUID taskId,
                                               String closingNotes) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }

        requireWorkable(sr);
        if (!isTaskActor(user, task)) {
            throw new IllegalStateException("Only the assigned fulfiller or staff can complete this task");
        }
        boolean canCompleteFromPending = "INSTANT".equals(task.getWorkflow())
                && task.getStatus() == FulfillmentTask.Status.PENDING;
        if (task.getStatus() != FulfillmentTask.Status.DELIVERED && !canCompleteFromPending) {
            throw new IllegalStateException("Only installed tasks can be completed");
        }
        if (closingNotes == null || closingNotes.isBlank()) {
            throw new IllegalStateException("Closing notes are required when completing a fulfillment task");
        }

        task.setStatus(FulfillmentTask.Status.COMPLETED);
        task.setClosingNotes(closingNotes.trim());
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

        recordActivity(sr, user.getId(), "TASK_COMPLETED", null,
                Map.of("taskId", task.getId(), "taskDescription", task.getDescription()));

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("requesterFirstName", firstName(sr.getRequester().getDisplayName()));
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        payload.put("fulfillerName", user.getDisplayName());
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "TASK_COMPLETED", payload));

        if (areAllTasksCompleted(sr)) {
            sr.setStatus(ServiceRequest.Status.FULFILLED);
            sr.setUpdatedBy(user.getId());
            sr.setUpdatedAt(OffsetDateTime.now());
            publishEvent(sr, "FULFILLED", user);
        }

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Stop the service-request SLA clock when the request is fulfilled.
        slaEngine.onServiceRequestStatusChanged(saved);

        return toResponse(saved);
    }

    @Transactional
    public ServiceRequestResponse setDeliveryDate(AppUser user, UUID orgId, UUID requestId,
                                                  UUID taskId, java.time.LocalDate expectedDeliveryDate) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }
        if (expectedDeliveryDate == null) {
            throw new IllegalStateException("expectedDeliveryDate is required");
        }
        requireWorkable(sr);
        if (!isTaskActor(user, task)) {
            throw new IllegalStateException("Only the assigned fulfiller or staff can set the delivery date");
        }
        if (task.getStatus() != FulfillmentTask.Status.ORDERED) {
            throw new IllegalStateException("Only ORDERED tasks can have a delivery date set");
        }

        task.setExpectedDeliveryDate(expectedDeliveryDate);
        task.setStatus(FulfillmentTask.Status.DELIVERY_DATE_SET);
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

        recordActivity(sr, user.getId(), "DELIVERY_DATE_SET", null,
                Map.of("taskId", task.getId(), "expectedDeliveryDate", expectedDeliveryDate.toString()));

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("requesterFirstName", firstName(sr.getRequester().getDisplayName()));
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        payload.put("expectedDeliveryDate", DateFormats.formatDate(expectedDeliveryDate));
        if (task.getAssignee() != null) {
            payload.put("assigneeId", task.getAssignee().getId());
            payload.put("assigneeName", task.getAssignee().getDisplayName());
            payload.put("assigneeFirstName", firstName(task.getAssignee().getDisplayName()));
        }
        payload.put("locationName", sr.getLocation() != null ? sr.getLocation().getName() : "the delivery location");
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "DELIVERY_DATE_SET", payload));

        return toResponse(sr);
    }

    @Transactional
    public ServiceRequestResponse markDelivered(AppUser user, UUID orgId, UUID requestId, UUID taskId) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));
        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }

        requireWorkable(sr);
        if (!isTaskActor(user, task)) {
            throw new IllegalStateException("Only the assigned fulfiller or staff can mark this task installed");
        }
        if (task.getStatus() != FulfillmentTask.Status.DELIVERY_DATE_SET) {
            throw new IllegalStateException("A delivery date must be set before marking the task installed");
        }

        task.setDeliveredAt(OffsetDateTime.now());
        task.setStatus(FulfillmentTask.Status.DELIVERED);
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

        recordActivity(sr, user.getId(), "DELIVERED", null,
                Map.of("taskId", task.getId(), "taskDescription", task.getDescription()));

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        payload.put("requesterFirstName", firstName(sr.getRequester().getDisplayName()));
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        payload.put("fulfillerName", user.getDisplayName());
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "DELIVERED", payload));

        return toResponse(sr);
    }

    private void seedFulfillmentTasks(AppUser user, ServiceRequest sr) {
        if (!fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId()).isEmpty()) {
            return;
        }
        CatalogItem item = sr.getCatalogItem();
        JsonNode template = item.getFulfillmentTasks();
        if (template == null || !template.isArray()) {
            return;
        }

        int order = 0;
        for (JsonNode t : template) {
            FulfillmentTask task = new FulfillmentTask();
            task.setServiceRequest(sr);
            task.setDescription(t.hasNonNull("description") ? t.get("description").asText() : "Fulfillment task");
            task.setSequenceOrder(t.hasNonNull("sequenceOrder") ? t.get("sequenceOrder").asInt() : order++);
            task.setCreatedBy(user.getId());
            task.setUpdatedBy(user.getId());
            if (t.hasNonNull("workflow")) {
                task.setWorkflow(t.get("workflow").asText("FULL"));
            }

            if (t.hasNonNull("assigneeId")) {
                UUID assigneeId = UUID.fromString(t.get("assigneeId").asText());
                AppUser assignee = appUserRepository.findById(assigneeId).orElse(null);
                task.setAssignee(assignee);
            }

            fulfillmentTaskRepository.save(task);
        }
    }

    private boolean areAllTasksCompleted(ServiceRequest sr) {
        List<FulfillmentTask> tasks = fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId());
        return !tasks.isEmpty() && tasks.stream().allMatch(t -> t.getStatus() == FulfillmentTask.Status.COMPLETED);
    }

    private String generateServiceRequestNumber() {
        Long next = ((Number) entityManager.createNativeQuery("SELECT nextval('service_request_number_seq')")
                .getSingleResult()).longValue();
        return "SR-" + next;
    }

    @Transactional
    public ServiceRequestResponse sendReminder(AppUser user, UUID orgId, UUID id, String message) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        if (!isAdminOrSuperAdmin(user)) {
            throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can send reminders");
        }
        if (sr.getStatus() != ServiceRequest.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("Only PENDING_APPROVAL requests can be reminded");
        }
        if (sr.getApprover() == null) {
            throw new IllegalStateException("No approver assigned to this request");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalStateException("A reminder message is required");
        }
        String trimmed = message.trim();

        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterName", sr.getRequester().getDisplayName());
        payload.put("submittedDate", sr.getCreatedAt() != null ? DateFormats.formatDateTime(sr.getCreatedAt()) : "");
        payload.put("adminName", user.getDisplayName());
        payload.put("reminderMessage", trimmed);
        payload.put("message", trimmed);
        payload.put("approverFirstName", firstName(sr.getApprover().getDisplayName()));
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());

        var content = notificationTemplateBuilder.forEvent("SERVICE_REQUEST_REMINDER", payload);
        notificationService.send(new NotificationRequest(
                sr.getOrgId(),
                sr.getApprover().getId(),
                "SERVICE_REQUEST_REMINDER",
                content.inAppSubject(),
                content.inAppBody(),
                "SERVICE_REQUEST",
                sr.getId(),
                Notification.Channel.BOTH,
                content));

        recordActivity(sr, user.getId(), "SEND_REMINDER", null,
                Map.of("comment", trimmed,
                        "approverId", sr.getApprover().getId(),
                        "approverName", sr.getApprover().getDisplayName()));

        return toResponse(sr);
    }

    private ServiceRequestResponse toResponse(ServiceRequest sr) {
        List<FulfillmentTask> tasks = fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId());

        return new ServiceRequestResponse(
                sr.getId(),
                sr.getNumber(),
                sr.getCatalogItem().getId(),
                sr.getCatalogItem().getName(),
                sr.getRequester().getId(),
                sr.getRequester().getDisplayName(),
                sr.getStatus(),
                sr.getFormData().toString(),
                sr.isApprovalRequired(),
                sr.getApprover() != null ? sr.getApprover().getId() : null,
                sr.getApprover() != null ? sr.getApprover().getDisplayName() : null,
                sr.getApprovalDecision(),
                sr.getApprovalComment(),
                sr.isApprovalBypassed(),
                sr.getBypassedBy() != null ? sr.getBypassedBy().getId() : null,
                sr.getBypassedBy() != null ? sr.getBypassedBy().getDisplayName() : null,
                sr.getDecidedAt(),
                sr.getNeededBy(),
                sr.getLocation() != null ? sr.getLocation().getId() : null,
                sr.getLocation() != null ? sr.getLocation().getName() : null,
                sr.getPhone(),
                tasks.stream()
                        .sorted(Comparator.comparingInt(FulfillmentTask::getSequenceOrder))
                        .map(t -> new FulfillmentTaskResponse(
                                t.getId(),
                                t.getDescription(),
                                t.getSequenceOrder(),
                                t.getStatus(),
                                t.getAssignee() != null ? t.getAssignee().getId() : null,
                                t.getAssignee() != null ? t.getAssignee().getDisplayName() : null,
                                t.getCompletedAt(),
                                t.getExpectedDeliveryDate(),
                                t.getDeliveredAt(),
                                t.getWorkflow(),
                                t.getOrderId(),
                                t.getVendor(),
                                t.getClosingNotes()))
                        .toList(),
                sr.getCreatedAt(),
                sr.getPriority() != null ? sr.getPriority().getId() : null,
                sr.getPriority() != null ? sr.getPriority().getName() : null
        );
    }
}
