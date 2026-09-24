package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.sla.SlaInstanceDetailResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.ChangeRequest;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Priority;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ServiceRequest;
import com.alignedcardio.itsm.entity.SlaInstance;
import com.alignedcardio.itsm.repository.FulfillmentTaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityGraph;
import jakarta.persistence.Subgraph;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SlaDetailsService {

    private final EntityManager entityManager;
    private final FulfillmentTaskRepository fulfillmentTaskRepository;

    public SlaDetailsService(EntityManager entityManager, FulfillmentTaskRepository fulfillmentTaskRepository) {
        this.entityManager = entityManager;
        this.fulfillmentTaskRepository = fulfillmentTaskRepository;
    }

    @Transactional(readOnly = true)
    public List<SlaInstanceDetailResponse> list(UUID orgId,
                                              List<String> breachStatus,
                                              String priority,
                                              OffsetDateTime dateFrom,
                                              OffsetDateTime dateTo,
                                              AppUser user,
                                              boolean mine,
                                              UUID incidentId,
                                              UUID serviceRequestId,
                                              UUID problemId,
                                              UUID changeId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SlaInstance> cq = cb.createQuery(SlaInstance.class);
        Root<SlaInstance> root = cq.from(SlaInstance.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));

        // Explicit LEFT joins honour each entity's @Where deletedAt filter, so a
        // non-null join id means "linked to a live, non-deleted ticket". The old
        // implicit-join dereferences were INNER joins and silently dropped every
        // SLA instance whose other FK was null — i.e. all SR-backed rows.
        jakarta.persistence.criteria.Join<SlaInstance, Incident> incident =
                root.join("incident", jakarta.persistence.criteria.JoinType.LEFT);
        jakarta.persistence.criteria.Join<SlaInstance, ServiceRequest> serviceRequest =
                root.join("serviceRequest", jakarta.persistence.criteria.JoinType.LEFT);
        jakarta.persistence.criteria.Join<SlaInstance, Problem> problem =
                root.join("problem", jakarta.persistence.criteria.JoinType.LEFT);
        jakarta.persistence.criteria.Join<SlaInstance, ChangeRequest> change =
                root.join("changeRequest", jakarta.persistence.criteria.JoinType.LEFT);
        predicates.add(cb.or(
                cb.isNotNull(incident.get("id")),
                cb.isNotNull(serviceRequest.get("id")),
                cb.isNotNull(problem.get("id")),
                cb.isNotNull(change.get("id"))));

        // Single-ticket lookups for the detail-page SLA panel.
        if (incidentId != null) {
            predicates.add(cb.equal(incident.get("id"), incidentId));
        }
        if (serviceRequestId != null) {
            predicates.add(cb.equal(serviceRequest.get("id"), serviceRequestId));
        }
        if (problemId != null) {
            predicates.add(cb.equal(problem.get("id"), problemId));
        }
        if (changeId != null) {
            predicates.add(cb.equal(change.get("id"), changeId));
        }

        if (mine && user != null) {
            UUID userId = user.getId();
            Predicate incidentAssignedToMe = cb.equal(incident.get("assignee").get("id"), userId);
            Predicate requestRequestedByMe = cb.equal(serviceRequest.get("requester").get("id"), userId);
            Predicate problemAssignedToMe = cb.equal(problem.get("assignee").get("id"), userId);
            Predicate changeAssignedToMe = cb.equal(change.get("assignee").get("id"), userId);
            List<UUID> assignedRequestIds = fulfillmentTaskRepository
                    .findByAssignee_IdAndDeletedAtIsNull(userId)
                    .stream()
                    .map(ft -> ft.getServiceRequest() == null ? null : ft.getServiceRequest().getId())
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!assignedRequestIds.isEmpty()) {
                Predicate requestAssignedToMe = serviceRequest.get("id").in(assignedRequestIds);
                predicates.add(cb.or(incidentAssignedToMe, requestRequestedByMe, requestAssignedToMe,
                        problemAssignedToMe, changeAssignedToMe));
            } else {
                predicates.add(cb.or(incidentAssignedToMe, requestRequestedByMe,
                        problemAssignedToMe, changeAssignedToMe));
            }
        }

        if (breachStatus != null && !breachStatus.isEmpty()) {
            List<SlaInstance.BreachStatus> statuses = breachStatus.stream()
                    .map(s -> s.trim())
                    .map(SlaInstance.BreachStatus::valueOf)
                    .toList();
            predicates.add(root.get("breachStatus").in(statuses));
        }

        if (priority != null && !priority.isBlank()) {
            // Priority filter must match SRs too — incidents alone would
            // silently drop every service-request row.
            predicates.add(cb.or(
                    cb.equal(incident.get("priority").get("name"), priority),
                    cb.equal(serviceRequest.get("priority").get("name"), priority)));
        }

        if (dateFrom != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
        }
        if (dateTo != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
        }

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<SlaInstance> query = entityManager.createQuery(cq);

        EntityGraph<SlaInstance> graph = entityManager.createEntityGraph(SlaInstance.class);
        Subgraph<Incident> incidentGraph = graph.addSubgraph("incident", Incident.class);
        incidentGraph.addAttributeNodes("number", "title");
        Subgraph<Priority> priorityGraph = incidentGraph.addSubgraph("priority", Priority.class);
        priorityGraph.addAttributeNodes("name");
        Subgraph<ServiceRequest> serviceRequestGraph = graph.addSubgraph("serviceRequest", ServiceRequest.class);
        serviceRequestGraph.addAttributeNodes("number");
        serviceRequestGraph.addSubgraph("priority", Priority.class).addAttributeNodes("name");
        Subgraph<CatalogItem> catalogItemGraph = serviceRequestGraph.addSubgraph("catalogItem", CatalogItem.class);
        catalogItemGraph.addAttributeNodes("name");
        graph.addSubgraph("problem", Problem.class).addAttributeNodes("number", "title");
        graph.addSubgraph("changeRequest", ChangeRequest.class).addAttributeNodes("number", "title");
        graph.addSubgraph("policy").addAttributeNodes("name", "workflowType");
        query.setHint("jakarta.persistence.fetchgraph", graph);

        List<SlaInstance> instances = query.getResultList().stream()
                .filter(this::isLinkedTicketNotDeleted)
                .toList();

        // Fulfiller = first task assignee per request — one batch query, no N+1.
        List<UUID> srIds = instances.stream()
                .map(SlaInstance::getServiceRequest)
                .filter(java.util.Objects::nonNull)
                .map(ServiceRequest::getId)
                .toList();
        Map<UUID, String> fulfillerBySr = new java.util.HashMap<>();
        if (!srIds.isEmpty()) {
            fulfillmentTaskRepository.findByServiceRequest_IdInAndAssigneeIsNotNull(srIds).stream()
                    .sorted(Comparator.comparingInt(FulfillmentTask::getSequenceOrder))
                    .forEach(t -> fulfillerBySr.putIfAbsent(
                            t.getServiceRequest().getId(), t.getAssignee().getDisplayName()));
        }

        Map<UUID, String> finalFulfillers = fulfillerBySr;
        return instances.stream().map(si -> toResponse(si, finalFulfillers)).toList();
    }

    // The Criteria predicate excludes deleted tickets, but a lazy
    // soft-deleted association can still materialize as a null join row or
    // throw EntityNotFoundException — post-filter defensively.
    private boolean isLinkedTicketNotDeleted(SlaInstance si) {
        try {
            Incident incident = si.getIncident();
            if (incident != null && incident.getDeletedAt() != null) {
                return false;
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return false;
        }
        try {
            ServiceRequest sr = si.getServiceRequest();
            if (sr != null && sr.getDeletedAt() != null) {
                return false;
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return false;
        }
        try {
            Problem problem = si.getProblem();
            if (problem != null && problem.getDeletedAt() != null) {
                return false;
            }
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return false;
        }
        try {
            ChangeRequest change = si.getChangeRequest();
            return change == null || change.getDeletedAt() == null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return false;
        }
    }

    private SlaInstanceDetailResponse toResponse(SlaInstance si, Map<UUID, String> fulfillerBySr) {
        Incident incident = si.getIncident();
        UUID incidentId = incident != null ? incident.getId() : null;
        Long incidentNumber = incident != null ? incident.getNumber() : null;
        String incidentTitle = incident != null ? incident.getTitle() : null;
        String incidentPriority = incident != null && incident.getPriority() != null ? incident.getPriority().getName() : null;

        ServiceRequest serviceRequest = si.getServiceRequest();
        UUID serviceRequestId = serviceRequest != null ? serviceRequest.getId() : null;
        String serviceRequestNumber = serviceRequest != null ? serviceRequest.getNumber() : null;
        String serviceRequestTitle = serviceRequest != null && serviceRequest.getCatalogItem() != null
                ? serviceRequest.getCatalogItem().getName()
                : null;
        String serviceRequestPriority = serviceRequest != null && serviceRequest.getPriority() != null
                ? serviceRequest.getPriority().getName()
                : null;
        String fulfillerName = serviceRequest != null
                ? fulfillerBySr.get(serviceRequest.getId())
                : null;

        Problem problem = si.getProblem();
        ChangeRequest change = si.getChangeRequest();

        String entityKind = incident != null ? "INCIDENT"
                : serviceRequest != null ? "SERVICE_REQUEST"
                : problem != null ? "PROBLEM"
                : change != null ? "CHANGE"
                : null;

        String policyName = si.getPolicy() != null ? si.getPolicy().getName() : null;
        String workflowType = si.getPolicy() != null ? si.getPolicy().getWorkflowType() : null;

        return new SlaInstanceDetailResponse(
                si.getId(),
                entityKind,
                incidentId,
                incidentNumber,
                incidentPriority,
                incidentTitle,
                serviceRequestId,
                serviceRequestNumber,
                serviceRequestTitle,
                serviceRequestPriority,
                fulfillerName,
                problem != null ? problem.getId() : null,
                problem != null ? problem.getNumber() : null,
                problem != null ? problem.getTitle() : null,
                change != null ? change.getId() : null,
                change != null ? change.getNumber() : null,
                change != null ? change.getTitle() : null,
                policyName,
                workflowType,
                si.getResponseDueAt(),
                si.getResolutionDueAt(),
                si.getResponseMetAt(),
                si.getResolutionMetAt(),
                si.getPausedAt(),
                si.getTotalPausedMinutes(),
                si.getBreachStatus()
        );
    }
}
