package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.api.reporting.ReportMetadataResponse;
import com.alignedcardio.itsm.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportingService {

    private static final int MAX_DATE_RANGE_MONTHS = 24;
    private static final int MAX_RESULT_ROWS = 1000;

    private static final Map<String, Class<?>> ENTITY_WHITELIST = Map.of(
            "incident", Incident.class,
            "issue", Issue.class,
            "problem", Problem.class,
            "change", ChangeRequest.class,
            "service_request", ServiceRequest.class);

    private static final Map<String, Set<String>> FIELDS_BY_ENTITY = Map.of(
            "incident", Set.of("orgId", "status", "priority", "category", "createdAt", "resolvedAt", "closedAt", "impact", "urgency"),
            "issue", Set.of("orgId", "status", "priority", "createdAt", "type"),
            "problem", Set.of("orgId", "status", "createdAt"),
            "change", Set.of("orgId", "status", "createdAt"),
            "service_request", Set.of("orgId", "status", "createdAt"));

    private static final Map<String, String> DATE_FIELD_BY_ENTITY = Map.of(
            "incident", "createdAt",
            "issue", "createdAt",
            "problem", "createdAt",
            "change", "createdAt",
            "service_request", "createdAt");

    private static final Set<String> ALLOWED_OPS = Set.of("eq", "ne", "in");

    private final EntityManager entityManager;

    public ReportingService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public ReportMetadataResponse getMetadata() {
        List<String> entities = new ArrayList<>(ENTITY_WHITELIST.keySet());
        Collections.sort(entities);

        Map<String, List<String>> fieldsByEntity = new LinkedHashMap<>();
        for (String entity : entities) {
            List<String> fields = new ArrayList<>(FIELDS_BY_ENTITY.getOrDefault(entity, Set.of()));
            Collections.sort(fields);
            fieldsByEntity.put(entity, fields);
        }

        List<String> operators = new ArrayList<>(ALLOWED_OPS);
        Collections.sort(operators);

        return new ReportMetadataResponse(entities, fieldsByEntity, operators, DATE_FIELD_BY_ENTITY);
    }

    public AdHocQueryResponse adHocQuery(UUID orgId, AdHocQueryRequest request) {
        Class<?> entityClass = ENTITY_WHITELIST.get(request.entity());
        if (entityClass == null) {
            throw new IllegalArgumentException("Unknown report entity: " + request.entity());
        }

        Set<String> allowedFields = FIELDS_BY_ENTITY.get(request.entity());
        if (allowedFields == null) {
            throw new IllegalArgumentException("No field whitelist for entity: " + request.entity());
        }

        if (request.groupBy() != null && !request.groupBy().isBlank() && !allowedFields.contains(request.groupBy())) {
            throw new IllegalArgumentException("Field not allowed for grouping: " + request.groupBy());
        }

        validateDateRange(request.dateRange());

        if (request.filters() != null) {
            for (AdHocQueryFilter filter : request.filters()) {
                if (!allowedFields.contains(filter.field())) {
                    throw new IllegalArgumentException("Field not allowed for filter: " + filter.field());
                }
                if (!ALLOWED_OPS.contains(filter.op())) {
                    throw new IllegalArgumentException("Operator not allowed: " + filter.op());
                }
            }
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(entityClass);

        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));

        applyDateRange(cb, root, request.entity(), request.dateRange()).ifPresent(predicates::add);

        if (request.filters() != null) {
            for (AdHocQueryFilter filter : request.filters()) {
                predicates.add(buildPredicate(cb, root, filter, entityClass));
            }
        }

        cq.where(predicates.toArray(new Predicate[0]));

        if (request.groupBy() != null && !request.groupBy().isBlank()) {
            Path<?> groupPath = root.get(request.groupBy());
            cq.groupBy(groupPath);
            cq.multiselect(groupPath, cb.count(root));
        } else {
            cq.multiselect(root, cb.count(root));
        }

        TypedQuery<Tuple> query = entityManager.createQuery(cq);
        query.setMaxResults(MAX_RESULT_ROWS);

        List<Tuple> tuples = query.getResultList();

        List<Map<String, Object>> rows = tuples.stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("group", t.get(0));
                    row.put("count", t.get(1));
                    return row;
                })
                .collect(Collectors.toList());

        return new AdHocQueryResponse(orgId, request.entity(), request.groupBy(), rows);
    }

    public Map<String, Object> ticketsSummary(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<Incident> root = total.from(Incident.class);
        total.select(cb.count(root));
        total.where(cb.equal(root.get("orgId"), orgId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> open = cb.createQuery(Long.class);
        Root<Incident> openRoot = open.from(Incident.class);
        open.select(cb.count(openRoot));
        open.where(
                cb.equal(openRoot.get("orgId"), orgId),
                cb.notEqual(openRoot.get("status"), Incident.Status.CLOSED));
        long openCount = entityManager.createQuery(open).getSingleResult();

        CriteriaQuery<Long> inProgress = cb.createQuery(Long.class);
        Root<Incident> ipRoot = inProgress.from(Incident.class);
        inProgress.select(cb.count(ipRoot));
        inProgress.where(
                cb.equal(ipRoot.get("orgId"), orgId),
                cb.equal(ipRoot.get("status"), Incident.Status.IN_PROGRESS));
        long inProgressCount = entityManager.createQuery(inProgress).getSingleResult();

        OffsetDateTime startOfDay = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        CriteriaQuery<Long> resolved = cb.createQuery(Long.class);
        Root<Incident> resRoot = resolved.from(Incident.class);
        resolved.select(cb.count(resRoot));
        resolved.where(
                cb.equal(resRoot.get("orgId"), orgId),
                cb.isNotNull(resRoot.get("resolvedAt")),
                cb.greaterThanOrEqualTo(resRoot.get("resolvedAt"), startOfDay));
        long resolvedToday = entityManager.createQuery(resolved).getSingleResult();

        return Map.of(
                "total", totalCount,
                "open", openCount,
                "inProgress", inProgressCount,
                "resolvedToday", resolvedToday);
    }

    public Map<String, Object> slaCompliance(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<SlaInstance> totalRoot = total.from(SlaInstance.class);
        total.select(cb.count(totalRoot));
        total.where(cb.equal(totalRoot.get("orgId"), orgId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> breached = cb.createQuery(Long.class);
        Root<SlaInstance> breachRoot = breached.from(SlaInstance.class);
        breachRoot.alias("b");
        breached.select(cb.count(breachRoot));
        breached.where(
                cb.equal(breachRoot.get("orgId"), orgId),
                cb.equal(breachRoot.get("breachStatus"), SlaInstance.BreachStatus.BREACHED));
        long breachedCount = entityManager.createQuery(breached).getSingleResult();

        double compliance = totalCount == 0 ? 100.0
                : ((totalCount - breachedCount) * 100.0 / totalCount);

        return Map.of(
                "total", totalCount,
                "breached", breachedCount,
                "compliancePercent", Math.round(compliance * 100.0) / 100.0);
    }

    public List<Map<String, Object>> agentWorkload(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Incident> root = cq.from(Incident.class);

        cq.multiselect(root.get("assignee").get("id"), cb.count(root));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.isNotNull(root.get("assignee")),
                cb.notEqual(root.get("status"), Incident.Status.CLOSED));
        cq.groupBy(root.get("assignee").get("id"));

        return entityManager.createQuery(cq)
                .getResultList()
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("agentId", t.get(0, UUID.class));
                    row.put("openCount", t.get(1, Long.class));
                    return row;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> sprintVelocity(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Issue> root = cq.from(Issue.class);

        cq.multiselect(root.get("sprint").get("id"), cb.count(root));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.isNotNull(root.get("sprint")));
        cq.groupBy(root.get("sprint").get("id"));

        return entityManager.createQuery(cq)
                .getResultList()
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("sprintId", t.get(0, UUID.class));
                    row.put("committed", t.get(1, Long.class));
                    row.put("completed", 0L);
                    return row;
                })
                .collect(Collectors.toList());
    }

    private void validateDateRange(AdHocQueryRequest.DateRange dateRange) {
        if (dateRange == null || dateRange.from() == null || dateRange.to() == null) {
            return;
        }
        if (dateRange.from().isAfter(dateRange.to())) {
            throw new IllegalArgumentException("Date range 'from' must be before 'to'");
        }
        if (dateRange.from().plus(Period.ofMonths(MAX_DATE_RANGE_MONTHS)).isBefore(dateRange.to())) {
            throw new IllegalArgumentException("Date range exceeds maximum span of " + MAX_DATE_RANGE_MONTHS + " months");
        }
    }

    private Optional<Predicate> applyDateRange(CriteriaBuilder cb, Root<?> root, String entity, AdHocQueryRequest.DateRange dateRange) {
        if (dateRange == null || dateRange.from() == null || dateRange.to() == null) {
            return Optional.empty();
        }
        String dateField = DATE_FIELD_BY_ENTITY.get(entity);
        if (dateField == null) {
            return Optional.empty();
        }
        Path<OffsetDateTime> path = root.get(dateField);
        return Optional.of(cb.between(path, dateRange.from(), dateRange.to()));
    }

    @SuppressWarnings("unchecked")
    private Predicate buildPredicate(CriteriaBuilder cb, Root<?> root, AdHocQueryFilter filter, Class<?> entityClass) {
        Path<Object> path = root.get(filter.field());
        Class<?> fieldClass = path.getJavaType();
        Object parsedValue = parseValue(filter.value(), fieldClass);

        return switch (filter.op()) {
            case "eq" -> cb.equal(path, parsedValue);
            case "ne" -> cb.notEqual(path, parsedValue);
            case "in" -> {
                List<Object> values = Arrays.stream(filter.value().split(","))
                        .map(String::trim)
                        .map(v -> parseValue(v, fieldClass))
                        .collect(Collectors.toList());
                yield path.in(values);
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + filter.op());
        };
    }

    private Object parseValue(String value, Class<?> fieldClass) {
        if (fieldClass.isEnum()) {
            return Enum.valueOf((Class<Enum>) fieldClass, value);
        }
        if (fieldClass == UUID.class) {
            return UUID.fromString(value);
        }
        if (fieldClass == OffsetDateTime.class || fieldClass == java.time.Instant.class) {
            return OffsetDateTime.parse(value);
        }
        if (fieldClass == Integer.class || fieldClass == int.class) {
            return Integer.parseInt(value);
        }
        if (fieldClass == Long.class || fieldClass == long.class) {
            return Long.parseLong(value);
        }
        return value;
    }
}
