package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.sla.SlaInstanceDetailResponse;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Priority;
import com.alignedcardio.itsm.entity.SlaInstance;
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

    public SlaDetailsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SlaInstanceDetailResponse> list(UUID orgId,
                                              List<String> breachStatus,
                                              String priority,
                                              OffsetDateTime dateFrom,
                                              OffsetDateTime dateTo) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<SlaInstance> cq = cb.createQuery(SlaInstance.class);
        Root<SlaInstance> root = cq.from(SlaInstance.class);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));

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
        graph.addSubgraph("policy").addAttributeNodes("name");
        query.setHint("jakarta.persistence.fetchgraph", graph);

        return query.getResultList().stream()
                .map(this::toResponse)
                .toList();
    }

    private SlaInstanceDetailResponse toResponse(SlaInstance si) {
        Incident incident = si.getIncident();
        UUID incidentId = incident != null ? incident.getId() : null;
        Long incidentNumber = incident != null ? incident.getNumber() : null;
        String incidentTitle = incident != null ? incident.getTitle() : null;
        String incidentPriority = incident != null && incident.getPriority() != null ? incident.getPriority().getName() : null;
        String policyName = si.getPolicy() != null ? si.getPolicy().getName() : null;

        return new SlaInstanceDetailResponse(
                si.getId(),
                incidentId,
                incidentNumber,
                incidentPriority,
                incidentTitle,
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
