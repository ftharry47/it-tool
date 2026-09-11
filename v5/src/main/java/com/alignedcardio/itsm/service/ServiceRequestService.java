package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.servicerequest.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Location;
import com.alignedcardio.itsm.entity.Notification;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
                                 SlaEngine slaEngine) {
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
        PhoneNumbers.requireValid(request.phone());
        sr.setPhone(request.phone());
        if (request.locationId() == null) {
            throw new IllegalStateException("Location is required for every service request");
        }
        Location location = locationRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.locationId())
                .orElseThrow(() -> new NotFoundException("Location not found"));
        sr.setLocation(location);
        sr.setApprovalRequired(computeApprovalRequired(item, sr.getFormData()));
        sr.setCreatedBy(user.getId());
        sr.setUpdatedBy(user.getId());

        ServiceRequest saved = serviceRequestRepository.save(sr);
        entityManager.flush();
        entityManager.refresh(saved);

        // Start the service-request SLA clock as soon as the request is created.
        slaEngine.onServiceRequestCreated(saved);

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
                .findByOrgIdAndApprover_IdAndApprovalDecisionAndDeletedAtIsNullOrderByCreatedAtDesc(
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
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("id", sr.getId());
        payload.put("number", sr.getNumber());
        payload.put("status", sr.getStatus().name());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("requesterId", sr.getRequester().getId());
        if (sr.getApprover() != null) {
            payload.put("approverId", sr.getApprover().getId());
            payload.put("approverName", sr.getApprover().getDisplayName());
        }
        if (sr.getApprovalComment() != null) {
            payload.put("reason", sr.getApprovalComment());
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

    private void notifyApproverOfRetroactiveApproval(AppUser sender, ServiceRequest sr, String reason) {
        if (sr.getApprover() == null) return;
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("number", sr.getNumber());
        payload.put("catalogItemName", sr.getCatalogItem().getName());
        payload.put("entityType", "SERVICE_REQUEST");
        payload.put("entityId", sr.getId());
        payload.put("actorName", sender.getDisplayName());
        payload.put("reason", reason);
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
        List<AppUser> admins = appUserRepository.findByOrgIdAndRoleNames(
                sr.getOrgId(), List.of("ADMIN", "SUPER_ADMIN"));
        for (AppUser admin : admins) {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("number", sr.getNumber());
            payload.put("catalogItemName", sr.getCatalogItem().getName());
            payload.put("entityType", "SERVICE_REQUEST");
            payload.put("entityId", sr.getId());
            payload.put("approverName", approver.getDisplayName());
            payload.put("comment", comment != null ? comment : "");
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
    }

    @Transactional
    public ServiceRequestResponse updateStatus(AppUser user, UUID orgId, UUID id, ServiceRequest.Status newStatus) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        boolean allCompleted = areAllTasksCompleted(sr);
        ServiceRequestStatusMachine.validate(sr, allCompleted, newStatus);
        sr.setStatus(newStatus);
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        ServiceRequest saved = serviceRequestRepository.save(sr);

        // Update the service-request SLA clock for pause/resume/stop.
        slaEngine.onServiceRequestStatusChanged(saved);

        return toResponse(saved);
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

        boolean isMember = teamMemberRepository.findByTeamId(IT_FULFILLMENT_TEAM_ID).stream()
                .anyMatch(tm -> tm.getUser() != null && tm.getUser().getId().equals(assigneeId));
        if (!isMember) {
            throw new IllegalStateException("Assignee must be a member of the IT Fulfillment team");
        }
        AppUser assignee = appUserRepository.findById(assigneeId)
                .orElseThrow(() -> new NotFoundException("Assignee not found"));

        task.setAssignee(assignee);
        task.setAssignedBy(user);
        task.setAssignedAt(OffsetDateTime.now());
        // Status stays PENDING: the fulfiller explicitly marks the task ORDERED
        // as the first progressive step after assignment.
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

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
                notificationPayload.put("title", task.getDescription());
                notificationPayload.put("actorName", user.getDisplayName());
                notificationPayload.put("assigneeName", assignee.getDisplayName());
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
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        eventPublisher.publishEvent(
                new ServiceRequestEvent(sr.getOrgId(), sr.getId(), "TASK_COMPLETED", payload));

        if (areAllTasksCompleted(sr)) {
            sr.setStatus(ServiceRequest.Status.FULFILLED);
            sr.setUpdatedBy(user.getId());
            sr.setUpdatedAt(OffsetDateTime.now());
            publishEvent(sr, "FULFILLED");
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
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
        payload.put("expectedDeliveryDate", DateFormats.formatDate(expectedDeliveryDate));
        if (task.getAssignee() != null) {
            payload.put("assigneeId", task.getAssignee().getId());
            payload.put("assigneeName", task.getAssignee().getDisplayName());
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
        payload.put("taskId", task.getId());
        payload.put("taskDescription", task.getDescription());
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
        payload.put("message", trimmed);
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
                                t.getVendor()))
                        .toList(),
                sr.getCreatedAt()
        );
    }
}
