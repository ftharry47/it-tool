package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.sla.SlaInstanceDetailResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.entity.FulfillmentTask;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Priority;
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
import java.util.List;
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
                                              boolean mine) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SlaInstance> cq = cb.createQuery(SlaInstance.class);
        Root<SlaInstance> root = cq.from(SlaInstance.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));

        // Exclude SLA rows whose backing ticket has been soft-deleted.
        Predicate incidentNotDeleted = cb.and(
                cb.isNotNull(root.get("incident")),
                cb.isNull(root.get("incident").get("deletedAt")));
        Predicate requestNotDeleted = cb.and(
                cb.isNotNull(root.get("serviceRequest")),
                cb.isNull(root.get("serviceRequest").get("deletedAt")));
        predicates.add(cb.or(incidentNotDeleted, requestNotDeleted));

        if (mine && user != null) {
            UUID userId = user.getId();
            Predicate incidentAssignedToMe = cb.equal(root.get("incident").get("assignee").get("id"), userId);
            Predicate requestRequestedByMe = cb.equal(root.get("serviceRequest").get("requester").get("id"), userId);
            List<UUID> assignedRequestIds = fulfillmentTaskRepository
                    .findByAssignee_IdAndDeletedAtIsNull(userId)
                    .stream()
                    .map(ft -> ft.getServiceRequest() == null ? null : ft.getServiceRequest().getId())
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!assignedRequestIds.isEmpty()) {
                Predicate requestAssignedToMe = root.get("serviceRequest").get("id").in(assignedRequestIds);
                predicates.add(cb.or(incidentAssignedToMe, requestRequestedByMe, requestAssignedToMe));
            } else {
                predicates.add(cb.or(incidentAssignedToMe, requestRequestedByMe));
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
            predicates.add(cb.equal(root.get("incident").get("priority").get("name"), priority));
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
        Subgraph<CatalogItem> catalogItemGraph = serviceRequestGraph.addSubgraph("catalogItem", CatalogItem.class);
        catalogItemGraph.addAttributeNodes("name");
        graph.addSubgraph("policy").addAttributeNodes("name");
        query.setHint("jakarta.persistence.fetchgraph", graph);

        // The Criteria predicate excludes deleted tickets, but a lazy
        // soft-deleted association can still materialize as a null join row or
        // throw EntityNotFoundException — post-filter defensively.
        return query.getResultList().stream()
                .filter(this::isLinkedTicketNotDeleted)
                .map(this::toResponse)
                .toList();
    }

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
            return sr == null || sr.getDeletedAt() == null;
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return false;
        }
    }

    private SlaInstanceDetailResponse toResponse(SlaInstance si) {
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

        String policyName = si.getPolicy() != null ? si.getPolicy().getName() : null;

        return new SlaInstanceDetailResponse(
                si.getId(),
                incidentId,
                incidentNumber,
                incidentPriority,
                incidentTitle,
                serviceRequestId,
                serviceRequestNumber,
                serviceRequestTitle,
                policyName,
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
