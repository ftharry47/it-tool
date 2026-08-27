package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.servicerequest.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.CatalogItemRepository;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceRequestService {

    private final ServiceRequestRepository serviceRequestRepository;
    private final CatalogItemRepository catalogItemRepository;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;
    private final AppUserRepository appUserRepository;
    private final FormSchemaValidator formSchemaValidator;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public ServiceRequestService(ServiceRequestRepository serviceRequestRepository,
                                 CatalogItemRepository catalogItemRepository,
                                 FulfillmentTaskRepository fulfillmentTaskRepository,
                                 AppUserRepository appUserRepository,
                                 FormSchemaValidator formSchemaValidator,
                                 ObjectMapper objectMapper,
                                 EntityManager entityManager) {
        this.serviceRequestRepository = serviceRequestRepository;
        this.catalogItemRepository = catalogItemRepository;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
        this.appUserRepository = appUserRepository;
        this.formSchemaValidator = formSchemaValidator;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> list(UUID orgId) {
        return serviceRequestRepository.findByOrgIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ServiceRequestResponse create(AppUser user, UUID orgId, ServiceRequestCreateRequest request) {
        CatalogItem item = catalogItemRepository.findByOrgIdAndId(orgId, request.catalogItemId())
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));

        formSchemaValidator.validate(item.getFormSchema(), request.formData());

        ServiceRequest sr = new ServiceRequest();
        sr.setOrgId(orgId);
        sr.setCatalogItem(item);
        sr.setRequester(user);
        sr.setFormData(request.formData());
        sr.setNeededBy(request.neededBy());
        sr.setApprovalRequired(item.isApprovalRequired());
        sr.setCreatedBy(user.getId());
        sr.setUpdatedBy(user.getId());

        ServiceRequest saved = serviceRequestRepository.save(sr);
        entityManager.flush();
        entityManager.refresh(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse get(UUID orgId, UUID id) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));
        return toResponse(sr);
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
        sr.setStatus(target);

        if (target == ServiceRequest.Status.IN_FULFILLMENT) {
            seedFulfillmentTasks(user, sr);
        }

        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        return toResponse(serviceRequestRepository.save(sr));
    }

    @Transactional
    public ServiceRequestResponse decide(AppUser user, UUID orgId, UUID id, ApprovalRequest request) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        if (sr.getStatus() != ServiceRequest.Status.PENDING_APPROVAL) {
            throw new IllegalStateException("Request is not pending approval");
        }

        CatalogItem item = sr.getCatalogItem();
        if (item.isApprovalRequired() && item.getApprover() != null && !item.getApprover().getId().equals(user.getId())) {
            throw new IllegalStateException("You are not the designated approver for this catalog item");
        }

        sr.setApprover(user);
        sr.setApprovalComment(request.comment());
        sr.setDecidedAt(OffsetDateTime.now());
        sr.setUpdatedBy(user.getId());
        sr.setUpdatedAt(OffsetDateTime.now());

        if (request.approve()) {
            sr.setApprovalDecision(ServiceRequest.ApprovalDecision.APPROVED);
            sr.setStatus(ServiceRequest.Status.APPROVED);
        } else {
            sr.setApprovalDecision(ServiceRequest.ApprovalDecision.REJECTED);
            sr.setStatus(ServiceRequest.Status.REJECTED);
        }

        if (sr.getStatus() == ServiceRequest.Status.APPROVED) {
            seedFulfillmentTasks(user, sr);
        }

        return toResponse(serviceRequestRepository.save(sr));
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

        return toResponse(serviceRequestRepository.save(sr));
    }

    @Transactional
    public ServiceRequestResponse completeTask(AppUser user, UUID orgId, UUID requestId, UUID taskId) {
        ServiceRequest sr = serviceRequestRepository.findByOrgIdAndId(orgId, requestId)
                .orElseThrow(() -> new NotFoundException("Service request not found"));

        FulfillmentTask task = fulfillmentTaskRepository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Task not found"));

        if (!task.getServiceRequest().getId().equals(sr.getId())) {
            throw new NotFoundException("Task does not belong to this request");
        }

        task.setStatus(FulfillmentTask.Status.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        task.setUpdatedBy(user.getId());
        task.setUpdatedAt(OffsetDateTime.now());
        fulfillmentTaskRepository.save(task);

        if (areAllTasksCompleted(sr)) {
            sr.setStatus(ServiceRequest.Status.FULFILLED);
            sr.setUpdatedBy(user.getId());
            sr.setUpdatedAt(OffsetDateTime.now());
        }

        return toResponse(serviceRequestRepository.save(sr));
    }

    private void seedFulfillmentTasks(AppUser user, ServiceRequest sr) {
        CatalogItem item = sr.getCatalogItem();
        if (item.getFulfillmentTasks() == null || item.getFulfillmentTasks().isBlank()) {
            return;
        }

        try {
            JsonNode template = objectMapper.readTree(item.getFulfillmentTasks());
            if (!template.isArray()) {
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

                if (t.hasNonNull("assigneeId")) {
                    UUID assigneeId = UUID.fromString(t.get("assigneeId").asText());
                    AppUser assignee = appUserRepository.findById(assigneeId).orElse(null);
                    task.setAssignee(assignee);
                }

                fulfillmentTaskRepository.save(task);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Invalid fulfillment task template", e);
        }
    }

    private boolean areAllTasksCompleted(ServiceRequest sr) {
        List<FulfillmentTask> tasks = fulfillmentTaskRepository.findByServiceRequestIdOrderBySequenceOrderAsc(sr.getId());
        return !tasks.isEmpty() && tasks.stream().allMatch(t -> t.getStatus() == FulfillmentTask.Status.COMPLETED);
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
                sr.getFormData(),
                sr.isApprovalRequired(),
                sr.getApprover() != null ? sr.getApprover().getId() : null,
                sr.getApprover() != null ? sr.getApprover().getDisplayName() : null,
                sr.getApprovalDecision(),
                sr.getApprovalComment(),
                sr.getDecidedAt(),
                sr.getNeededBy(),
                tasks.stream()
                        .sorted(Comparator.comparingInt(FulfillmentTask::getSequenceOrder))
                        .map(t -> new FulfillmentTaskResponse(
                                t.getId(),
                                t.getDescription(),
                                t.getSequenceOrder(),
                                t.getStatus(),
                                t.getAssignee() != null ? t.getAssignee().getId() : null,
                                t.getAssignee() != null ? t.getAssignee().getDisplayName() : null,
                                t.getCompletedAt()))
                        .toList(),
                sr.getCreatedAt()
        );
    }
}
