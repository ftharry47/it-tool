package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.api.reporting.ReportMetadataResponse;
import com.alignedcardio.itsm.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
            "incident", Set.of("orgId", "status", "priority", "category", "assignee", "requester", "createdAt", "resolvedAt", "closedAt", "impact", "urgency"),
            "issue", Set.of("orgId", "status", "priority", "createdAt", "assignee", "reporter", "type"),
            "problem", Set.of("orgId", "status", "createdAt"),
            "change", Set.of("orgId", "status", "createdAt", "requester"),
            "service_request", Set.of("orgId", "status", "createdAt", "requester", "catalogItem"));

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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
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

        boolean hasGroupBy = request.groupBy() != null && !request.groupBy().isBlank();

        if (hasGroupBy) {
            Path<?> rawPath = root.get(request.groupBy());
            Expression<?> groupExpr;
            if (BaseEntity.class.isAssignableFrom(rawPath.getJavaType())) {
                Join<?, ?> join = root.join(request.groupBy(), JoinType.LEFT);
                Path<String> namePath = join.get("name");
                groupExpr = cb.coalesce(namePath, cb.literal("Unassigned"));
            } else {
                groupExpr = rawPath;
            }
            cq.groupBy(groupExpr);
            cq.multiselect(groupExpr, cb.count(root));
        } else {
            cq.multiselect(cb.count(root));
        }

        TypedQuery<Tuple> query = entityManager.createQuery(cq);
        if (hasGroupBy) {
            query.setMaxResults(MAX_RESULT_ROWS);
        }

        List<Tuple> tuples = query.getResultList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : tuples) {
            Map<String, Object> row = new HashMap<>();
            if (hasGroupBy) {
                row.put("group", formatGroupValue(t.get(0)));
                row.put("count", t.get(1));
            } else {
                row.put("group", null);
                row.put("count", t.get(0));
            }
            rows.add(row);
        }

        return new AdHocQueryResponse(orgId, request.entity(), request.groupBy(), rows);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public AdHocQueryResponse priorityBreakdown(UUID orgId) {
        String jpql = """
                SELECT COALESCE(p.name, 'Unassigned'), COUNT(i)
                FROM Incident i
                LEFT JOIN i.priority p
                WHERE i.orgId = :orgId
                  AND i.status IN :statuses
                GROUP BY p.name
                """;

        List<Incident.Status> statuses = List.of(
                Incident.Status.NEW,
                Incident.Status.IN_PROGRESS,
                Incident.Status.ON_HOLD,
                Incident.Status.REOPENED);

        TypedQuery<Tuple> query = entityManager.createQuery(jpql, Tuple.class);
        query.setParameter("orgId", orgId);
        query.setParameter("statuses", statuses);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : query.getResultList()) {
            Map<String, Object> row = new HashMap<>();
            row.put("group", t.get(0, String.class));
            row.put("count", t.get(1, Long.class));
            rows.add(row);
        }

        return new AdHocQueryResponse(orgId, "incident", "priority", rows);
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

        CriteriaQuery<Long> unassigned = cb.createQuery(Long.class);
        Root<Incident> unRoot = unassigned.from(Incident.class);
        unassigned.select(cb.count(unRoot));
        unassigned.where(
                cb.equal(unRoot.get("orgId"), orgId),
                cb.isNull(unRoot.get("assignee")),
                unRoot.get("status").in(Incident.Status.NEW, Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD, Incident.Status.REOPENED));
        long unassignedCount = entityManager.createQuery(unassigned).getSingleResult();

        return Map.of(
                "total", totalCount,
                "open", openCount,
                "inProgress", inProgressCount,
                "resolvedToday", resolvedToday,
                "unassigned", unassignedCount);
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsTrend(UUID orgId, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<OffsetDateTime> cq = cb.createQuery(OffsetDateTime.class);
        Root<Incident> root = cq.from(Incident.class);
        cq.select(root.get("createdAt"));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), start));

        List<OffsetDateTime> dates = entityManager.createQuery(cq).getResultList();

        Map<LocalDate, Long> counts = dates.stream()
                .collect(Collectors.groupingBy(
                        d -> d.toInstant().atZone(ZoneOffset.UTC).toLocalDate(),
                        TreeMap::new,
                        Collectors.counting()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = OffsetDateTime.now(ZoneOffset.UTC).minusDays(i).toLocalDate();
            long count = counts.getOrDefault(date, 0L);
            result.add(Map.of("date", date.toString(), "count", count));
        }
        return result;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> slaComplianceTrend(UUID orgId, int days) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SlaInstance> root = cq.from(SlaInstance.class);
        cq.multiselect(root.get("createdAt"), root.get("breachStatus"));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), start));

        List<Tuple> rows = entityManager.createQuery(cq).getResultList();

        record DailySla(long total, long breached) {}

        Map<LocalDate, DailySla> byDay = new TreeMap<>();
        for (Tuple t : rows) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            SlaInstance.BreachStatus status = t.get(1, SlaInstance.BreachStatus.class);
            if (createdAt == null) continue;
            LocalDate date = createdAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            DailySla current = byDay.getOrDefault(date, new DailySla(0L, 0L));
            boolean isBreached = status == SlaInstance.BreachStatus.BREACHED;
            byDay.put(date, new DailySla(current.total() + 1, current.breached() + (isBreached ? 1 : 0)));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = OffsetDateTime.now(ZoneOffset.UTC).minusDays(i).toLocalDate();
            DailySla daily = byDay.getOrDefault(date, new DailySla(0L, 0L));
            double compliance = daily.total() == 0 ? 100.0
                    : ((daily.total() - daily.breached()) * 100.0 / daily.total());
            result.add(Map.of(
                    "date", date.toString(),
                    "total", daily.total(),
                    "breached", daily.breached(),
                    "compliancePercent", Math.round(compliance * 100.0) / 100.0));
        }
        return result;
    }

    public List<Map<String, Object>> agentWorkload(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Incident> root = cq.from(Incident.class);
        Join<Incident, AppUser> assignee = root.join("assignee");
        Expression<String> agentName = cb.coalesce(assignee.get("displayName"), assignee.get("email"));

        cq.multiselect(agentName, cb.count(root));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                root.get("status").in(
                        Incident.Status.NEW,
                        Incident.Status.IN_PROGRESS,
                        Incident.Status.ON_HOLD,
                        Incident.Status.WAITING_ON_CUSTOMER,
                        Incident.Status.REOPENED));
        cq.groupBy(agentName);

        return entityManager.createQuery(cq)
                .getResultList()
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("agentName", t.get(0, String.class));
                    row.put("openCount", t.get(1, Long.class));
                    return row;
                })
                .collect(Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> slaComplianceMonthly(UUID orgId, int months) {
        if (months < 1 || months > 60) {
            throw new IllegalArgumentException("months must be between 1 and 60");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(months).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SlaInstance> root = cq.from(SlaInstance.class);
        cq.multiselect(root.get("createdAt"), root.get("breachStatus"));
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.greaterThanOrEqualTo(root.get("createdAt"), start));

        List<Tuple> rows = entityManager.createQuery(cq).getResultList();

        record MonthlySla(long total, long breached) {}

        Map<YearMonth, MonthlySla> byMonth = new TreeMap<>();
        for (Tuple t : rows) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            SlaInstance.BreachStatus status = t.get(1, SlaInstance.BreachStatus.class);
            if (createdAt == null) continue;
            YearMonth month = YearMonth.from(createdAt);
            MonthlySla current = byMonth.getOrDefault(month, new MonthlySla(0L, 0L));
            boolean isBreached = status == SlaInstance.BreachStatus.BREACHED;
            byMonth.put(month, new MonthlySla(current.total() + 1, current.breached() + (isBreached ? 1 : 0)));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = YearMonth.now(ZoneOffset.UTC).minusMonths(i);
            MonthlySla monthly = byMonth.getOrDefault(month, new MonthlySla(0L, 0L));
            double compliance = monthly.total() == 0 ? 100.0
                    : ((monthly.total() - monthly.breached()) * 100.0 / monthly.total());
            result.add(Map.of(
                    "month", month.toString(),
                    "total", monthly.total(),
                    "breached", monthly.breached(),
                    "compliancePercent", Math.round(compliance * 100.0) / 100.0));
        }
        return result;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> slaComplianceOverall(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<SlaInstance> totalRoot = total.from(SlaInstance.class);
        total.select(cb.count(totalRoot));
        total.where(cb.equal(totalRoot.get("orgId"), orgId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> breached = cb.createQuery(Long.class);
        Root<SlaInstance> breachRoot = breached.from(SlaInstance.class);
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

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> sprintVelocity(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Issue> root = cq.from(Issue.class);
        Join<Issue, Sprint> sprint = root.join("sprint");
        Join<Issue, WorkflowStatus> workflowStatus = root.join("workflowStatus");

        Expression<Long> committed = cb.count(root);
        Expression<Long> completed = cb.sum(
                cb.<Long>selectCase()
                        .when(cb.equal(workflowStatus.get("category"), WorkflowStatus.Category.DONE), 1L)
                        .otherwise(0L));

        cq.multiselect(sprint.get("name"), committed, completed);
        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.isNull(root.get("deletedAt")),
                cb.isNull(sprint.get("deletedAt")));
        cq.groupBy(sprint.get("id"), sprint.get("name"));

        return entityManager.createQuery(cq)
                .getResultList()
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("sprintName", t.get(0, String.class));
                    row.put("committed", t.get(1, Long.class));
                    row.put("completed", t.get(2, Long.class));
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

    private Predicate buildPredicate(CriteriaBuilder cb, Root<?> root, AdHocQueryFilter filter, Class<?> entityClass) {
        Path<Object> path = root.get(filter.field());
        Class<?> fieldClass = path.getJavaType();

        if (BaseEntity.class.isAssignableFrom(fieldClass)) {
            return buildEntityPredicate(cb, path, filter, fieldClass);
        }

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

    @SuppressWarnings("unchecked")
    private Predicate buildEntityPredicate(CriteriaBuilder cb, Path<Object> path, AdHocQueryFilter filter, Class<?> fieldClass) {
        return switch (filter.op()) {
            case "eq" -> cb.equal(path, entityManager.getReference((Class<? extends BaseEntity>) fieldClass, UUID.fromString(filter.value())));
            case "ne" -> cb.notEqual(path, entityManager.getReference((Class<? extends BaseEntity>) fieldClass, UUID.fromString(filter.value())));
            case "in" -> {
                List<BaseEntity> values = Arrays.stream(filter.value().split(","))
                        .map(String::trim)
                        .map(v -> entityManager.getReference((Class<? extends BaseEntity>) fieldClass, UUID.fromString(v)))
                        .collect(Collectors.toList());
                yield path.in(values);
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + filter.op());
        };
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
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

    private Object formatGroupValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }
        if (value instanceof BaseEntity entity) {
            return labelForEntity(entity);
        }
        return value;
    }

    private String labelForEntity(BaseEntity entity) {
        if (entity.getId() == null) {
            return "Unknown";
        }
        for (String method : List.of("getDisplayName", "getEmail", "getName")) {
            try {
                java.lang.reflect.Method getter = entity.getClass().getMethod(method);
                Object name = getter.invoke(entity);
                if (name != null && !name.toString().isBlank()) {
                    return name.toString();
                }
            } catch (Exception ignored) {
            }
        }
        return entity.getId().toString();
    }
}
