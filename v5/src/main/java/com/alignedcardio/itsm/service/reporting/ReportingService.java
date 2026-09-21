package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.api.reporting.ReportMetadataResponse;
import com.alignedcardio.itsm.api.reporting.TicketsByLocationResponse;
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
            "incident", Set.of("orgId", "status", "priority", "category", "assignee", "requester", "createdAt", "resolvedAt", "closedAt", "impact", "urgency", "location"),
            "issue", Set.of("orgId", "status", "priority", "createdAt", "assignee", "reporter", "type"),
            "problem", Set.of("orgId", "status", "assignee", "createdAt"),
            "change", Set.of("orgId", "status", "assignee", "requestedBy", "changeType", "risk", "createdAt"),
            "service_request", Set.of("orgId", "status", "createdAt", "requester", "catalogItem", "location"));

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
    public AdHocQueryResponse adHocQuery(UUID orgId, AdHocQueryRequest request, UUID mineUserId) {
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
        predicates.add(cb.isNull(root.get("deletedAt")));

        if (mineUserId != null) {
            String mineField = switch (request.entity()) {
                case "incident", "issue", "problem" -> "assignee";
                case "service_request" -> "requester";
                case "change" -> "assignee";
                default -> null;
            };
            if (mineField != null) {
                predicates.add(cb.equal(root.get(mineField).get("id"), mineUserId));
            }
        }

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
                  AND i.deletedAt IS NULL
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

    public Map<String, Object> ticketsSummary(UUID orgId, UUID mineUserId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<Incident> root = total.from(Incident.class);
        total.select(cb.count(root));
        total.where(baseIncidentPredicates(cb, root, orgId, mineUserId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> open = cb.createQuery(Long.class);
        Root<Incident> openRoot = open.from(Incident.class);
        open.select(cb.count(openRoot));
        open.where(
                cb.and(baseIncidentPredicates(cb, openRoot, orgId, mineUserId)),
                cb.notEqual(openRoot.get("status"), Incident.Status.CLOSED));
        long openCount = entityManager.createQuery(open).getSingleResult();

        CriteriaQuery<Long> inProgress = cb.createQuery(Long.class);
        Root<Incident> ipRoot = inProgress.from(Incident.class);
        inProgress.select(cb.count(ipRoot));
        inProgress.where(
                cb.and(baseIncidentPredicates(cb, ipRoot, orgId, mineUserId)),
                cb.equal(ipRoot.get("status"), Incident.Status.IN_PROGRESS));
        long inProgressCount = entityManager.createQuery(inProgress).getSingleResult();

        OffsetDateTime startOfDay = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        CriteriaQuery<Long> resolved = cb.createQuery(Long.class);
        Root<Incident> resRoot = resolved.from(Incident.class);
        resolved.select(cb.count(resRoot));
        resolved.where(
                cb.and(baseIncidentPredicates(cb, resRoot, orgId, mineUserId)),
                cb.isNotNull(resRoot.get("resolvedAt")),
                cb.greaterThanOrEqualTo(resRoot.get("resolvedAt"), startOfDay));
        long resolvedToday = entityManager.createQuery(resolved).getSingleResult();

        CriteriaQuery<Long> unassigned = cb.createQuery(Long.class);
        Root<Incident> unRoot = unassigned.from(Incident.class);
        unassigned.select(cb.count(unRoot));
        unassigned.where(
                cb.equal(unRoot.get("orgId"), orgId),
                mineUserId == null ? cb.isNull(unRoot.get("assignee")) : cb.equal(unRoot.get("assignee").get("id"), mineUserId),
                unRoot.get("status").in(Incident.Status.NEW, Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD, Incident.Status.REOPENED));
        long unassignedCount = mineUserId == null
                ? entityManager.createQuery(unassigned).getSingleResult()
                : 0L;

        return Map.of(
                "total", totalCount,
                "open", openCount,
                "inProgress", inProgressCount,
                "resolvedToday", resolvedToday,
                "unassigned", unassignedCount);
    }

    private Predicate[] baseIncidentPredicates(CriteriaBuilder cb, Root<Incident> root, UUID orgId, UUID mineUserId) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));
        predicates.add(cb.isNull(root.get("deletedAt")));
        if (mineUserId != null) {
            predicates.add(cb.equal(root.get("assignee").get("id"), mineUserId));
        }
        return predicates.toArray(new Predicate[0]);
    }

    private Predicate[] baseSlaInstancePredicates(CriteriaBuilder cb, Root<SlaInstance> root, UUID orgId, UUID mineUserId) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(cb.equal(root.get("orgId"), orgId));
        // Explicit LEFT joins honour each entity's @Where deletedAt filter, so a
        // non-null join id means "linked to a live, non-deleted ticket". The old
        // implicit-join dereferences (root.get("incident").get("deletedAt")) were
        // INNER joins and silently dropped every SLA instance whose other FK was
        // null — i.e. all service-request-backed rows.
        Join<SlaInstance, Incident> incident = root.join("incident", JoinType.LEFT);
        Join<SlaInstance, ServiceRequest> serviceRequest = root.join("serviceRequest", JoinType.LEFT);
        Join<SlaInstance, Problem> problem = root.join("problem", JoinType.LEFT);
        Join<SlaInstance, ChangeRequest> change = root.join("changeRequest", JoinType.LEFT);
        predicates.add(cb.or(
                cb.isNotNull(incident.get("id")),
                cb.isNotNull(serviceRequest.get("id")),
                cb.isNotNull(problem.get("id")),
                cb.isNotNull(change.get("id"))));
        if (mineUserId != null) {
            predicates.add(cb.or(
                    cb.equal(incident.get("assignee").get("id"), mineUserId),
                    cb.equal(serviceRequest.get("requester").get("id"), mineUserId),
                    cb.equal(problem.get("assignee").get("id"), mineUserId),
                    cb.equal(change.get("assignee").get("id"), mineUserId)));
        }
        return predicates.toArray(new Predicate[0]);
    }

    private boolean isBreachedAtDue(OffsetDateTime resolutionDueAt, OffsetDateTime resolutionMetAt, OffsetDateTime now) {
        if (resolutionDueAt == null) {
            return false;
        }
        if (resolutionMetAt == null) {
            return now.isAfter(resolutionDueAt);
        }
        return resolutionMetAt.isAfter(resolutionDueAt);
    }

    /**
     * Admin view: current-month SLA compliance overall, plus breakdowns by
     * team and by individual agent. Ownership attribution: incident → assignee
     * (team = assignmentTeam), problem/change → assignee (team = their first
     * team membership), service request → agents holding its fulfillment tasks
     * (falls back to the requester when unworked). Deleted-ticket rows are
     * skipped.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> slaComplianceBreakdown(UUID orgId) {
        OffsetDateTime now = OffsetDateTime.now();
        YearMonth month = YearMonth.now();
        OffsetDateTime from = month.atDay(1).atStartOfDay().atOffset(now.getOffset());

        List<SlaInstance> instances = entityManager.createQuery(
                "SELECT si FROM SlaInstance si WHERE si.orgId = :org AND si.createdAt >= :from",
                SlaInstance.class)
                .setParameter("org", orgId)
                .setParameter("from", from)
                .getResultList();

        // Service-request ownership: agents holding a non-deleted fulfillment task.
        List<UUID> requestIds = instances.stream()
                .map(SlaInstance::getServiceRequest)
                .filter(Objects::nonNull)
                .map(sr -> {
                    try { return sr.getId(); } catch (jakarta.persistence.EntityNotFoundException e) { return null; }
                })
                .filter(Objects::nonNull)
                .toList();
        Map<UUID, List<AppUser>> taskOwnersByRequest = new HashMap<>();
        if (!requestIds.isEmpty()) {
            for (Tuple t : entityManager.createQuery(
                    "SELECT ft.serviceRequest.id, a.id, a.displayName FROM FulfillmentTask ft " +
                            "JOIN ft.assignee a WHERE ft.serviceRequest.id IN :ids AND ft.deletedAt IS NULL",
                    Tuple.class)
                    .setParameter("ids", requestIds)
                    .getResultList()) {
                AppUser owner = new AppUser();
                owner.setId(t.get(1, UUID.class));
                owner.setDisplayName(t.get(2, String.class));
                taskOwnersByRequest.computeIfAbsent(t.get(0, UUID.class), k -> new ArrayList<>())
                        .add(owner);
            }
        }

        // user -> first team (for non-incident tickets which carry no team field).
        Map<UUID, String> teamByUser = new HashMap<>();
        for (Tuple t : entityManager.createQuery(
                "SELECT tm.userId, t.name FROM TeamMember tm JOIN tm.team t WHERE t.orgId = :org",
                Tuple.class)
                .setParameter("org", orgId)
                .getResultList()) {
            teamByUser.putIfAbsent(t.get(0, UUID.class), t.get(1, String.class));
        }

        Map<UUID, long[]> byAgent = new LinkedHashMap<>();
        Map<UUID, String> agentNames = new HashMap<>();
        Map<String, long[]> byTeam = new LinkedHashMap<>();
        long[] overall = new long[2]; // [total, breached]

        for (SlaInstance si : instances) {
            boolean breached = isBreachedAtDue(si.getResolutionDueAt(), si.getResolutionMetAt(), now);
            try {
                if (si.getIncident() != null) {
                    Incident i = si.getIncident();
                    if (i.getDeletedAt() != null) continue;
                    recordBreakdown(byAgent, agentNames, byTeam, overall, breached,
                            i.getAssignee(),
                            i.getAssignmentTeam() != null ? i.getAssignmentTeam().getName()
                                    : teamByUser.get(i.getAssignee() != null ? i.getAssignee().getId() : null));
                } else if (si.getServiceRequest() != null) {
                    ServiceRequest sr = si.getServiceRequest();
                    if (sr.getDeletedAt() != null) continue;
                    List<AppUser> owners = taskOwnersByRequest.getOrDefault(sr.getId(), List.of());
                    if (owners.isEmpty() && sr.getRequester() != null) {
                        owners = List.of(sr.getRequester());
                    }
                    if (owners.isEmpty()) {
                        recordBreakdown(byAgent, agentNames, byTeam, overall, breached, null, null);
                    }
                    for (AppUser owner : owners) {
                        recordBreakdown(byAgent, agentNames, byTeam, overall, breached,
                                owner, teamByUser.get(owner.getId()));
                    }
                } else if (si.getProblem() != null) {
                    Problem p = si.getProblem();
                    if (p.getDeletedAt() != null) continue;
                    recordBreakdown(byAgent, agentNames, byTeam, overall, breached,
                            p.getAssignee(),
                            teamByUser.get(p.getAssignee() != null ? p.getAssignee().getId() : null));
                } else if (si.getChangeRequest() != null) {
                    ChangeRequest c = si.getChangeRequest();
                    if (c.getDeletedAt() != null) continue;
                    recordBreakdown(byAgent, agentNames, byTeam, overall, breached,
                            c.getAssignee(),
                            teamByUser.get(c.getAssignee() != null ? c.getAssignee().getId() : null));
                }
            } catch (jakarta.persistence.EntityNotFoundException e) {
                // Lazy association backed by a soft-deleted row — skip.
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", month.toString());
        result.put("overall", complianceMap(overall[0], overall[1]));
        result.put("byAgent", byAgent.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>(complianceMap(e.getValue()[0], e.getValue()[1]));
                    row.put("agentId", e.getKey());
                    row.put("agentName", e.getKey() != null ? agentNames.getOrDefault(e.getKey(), "Unknown") : "Unassigned");
                    return row;
                })
                .toList());
        result.put("byTeam", byTeam.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>(complianceMap(e.getValue()[0], e.getValue()[1]));
                    row.put("teamName", e.getKey());
                    return row;
                })
                .toList());
        return result;
    }

    private void recordBreakdown(Map<UUID, long[]> byAgent, Map<UUID, String> agentNames,
                                 Map<String, long[]> byTeam, long[] overall,
                                 boolean breached, AppUser owner, String teamName) {
        overall[0]++;
        if (breached) overall[1]++;
        UUID agentKey = owner != null ? owner.getId() : null;
        byAgent.computeIfAbsent(agentKey, k -> new long[2])[0]++;
        if (breached) byAgent.get(agentKey)[1]++;
        if (owner != null && owner.getDisplayName() != null) {
            agentNames.putIfAbsent(owner.getId(), owner.getDisplayName());
        }
        String teamKey = teamName != null ? teamName : "Unassigned";
        byTeam.computeIfAbsent(teamKey, k -> new long[2])[0]++;
        if (breached) byTeam.get(teamKey)[1]++;
    }

    private Map<String, Object> complianceMap(long total, long breached) {
        double pct = total == 0 ? 100.0 : Math.round((total - breached) * 10000.0 / total) / 100.0;
        return Map.of("total", total, "breached", breached, "compliancePercent", pct);
    }

    public Map<String, Object> slaCompliance(UUID orgId, UUID mineUserId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<SlaInstance> totalRoot = total.from(SlaInstance.class);
        total.select(cb.count(totalRoot));
        total.where(baseSlaInstancePredicates(cb, totalRoot, orgId, mineUserId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> breached = cb.createQuery(Long.class);
        Root<SlaInstance> breachRoot = breached.from(SlaInstance.class);
        breachRoot.alias("b");
        breached.select(cb.count(breachRoot));
        OffsetDateTime now = OffsetDateTime.now();
        breached.where(
                cb.and(baseSlaInstancePredicates(cb, breachRoot, orgId, mineUserId)),
                cb.isNotNull(breachRoot.get("resolutionDueAt")),
                cb.or(
                        cb.and(
                                cb.isNotNull(breachRoot.get("resolutionMetAt")),
                                cb.greaterThan(breachRoot.get("resolutionMetAt"), breachRoot.get("resolutionDueAt"))),
                        cb.and(
                                cb.isNull(breachRoot.get("resolutionMetAt")),
                                cb.lessThan(breachRoot.get("resolutionDueAt"), now))));
        long breachedCount = entityManager.createQuery(breached).getSingleResult();

        double compliance = totalCount == 0 ? 100.0
                : ((totalCount - breachedCount) * 100.0 / totalCount);

        return Map.of(
                "total", totalCount,
                "breached", breachedCount,
                "compliancePercent", Math.round(compliance * 100.0) / 100.0);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsTrend(UUID orgId, int days, UUID mineUserId) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<OffsetDateTime> cq = cb.createQuery(OffsetDateTime.class);
        Root<Incident> root = cq.from(Incident.class);
        cq.select(root.get("createdAt"));
        cq.where(
                cb.and(baseIncidentPredicates(cb, root, orgId, mineUserId)),
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
    public List<Map<String, Object>> slaComplianceTrend(UUID orgId, int days, UUID mineUserId) {
        if (days < 1 || days > 365) {
            throw new IllegalArgumentException("days must be between 1 and 365");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusDays(days).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SlaInstance> root = cq.from(SlaInstance.class);
        cq.multiselect(root.get("createdAt"), root.get("resolutionDueAt"), root.get("resolutionMetAt"));
        cq.where(
                cb.and(baseSlaInstancePredicates(cb, root, orgId, mineUserId)),
                cb.greaterThanOrEqualTo(root.get("createdAt"), start));

        List<Tuple> rows = entityManager.createQuery(cq).getResultList();

        record DailySla(long total, long breached) {}

        Map<LocalDate, DailySla> byDay = new TreeMap<>();
        for (Tuple t : rows) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            OffsetDateTime resolutionDueAt = t.get(1, OffsetDateTime.class);
            OffsetDateTime resolutionMetAt = t.get(2, OffsetDateTime.class);
            if (createdAt == null) continue;
            LocalDate date = createdAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            DailySla current = byDay.getOrDefault(date, new DailySla(0L, 0L));
            boolean isBreached = isBreachedAtDue(resolutionDueAt, resolutionMetAt, OffsetDateTime.now());
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

    private static final List<Incident.Status> WORKLOAD_INCIDENT_STATUSES = List.of(
            Incident.Status.NEW, Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD,
            Incident.Status.WAITING_ON_CUSTOMER, Incident.Status.REOPENED);
    private static final List<Problem.Status> WORKLOAD_PROBLEM_STATUSES = List.of(
            Problem.Status.NEW, Problem.Status.INVESTIGATING, Problem.Status.KNOWN_ERROR);
    private static final List<ChangeRequest.Status> WORKLOAD_CHANGE_STATUSES = List.of(
            ChangeRequest.Status.DRAFT, ChangeRequest.Status.PENDING_APPROVAL,
            ChangeRequest.Status.APPROVED, ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS);
    private static final List<FulfillmentTask.Status> WORKLOAD_TASK_STATUSES = List.of(
            FulfillmentTask.Status.PENDING, FulfillmentTask.Status.ORDERED,
            FulfillmentTask.Status.DELIVERY_DATE_SET);
    private static final List<ServiceRequest.Status> TERMINAL_REQUEST_STATUSES = List.of(
            ServiceRequest.Status.FULFILLED, ServiceRequest.Status.REJECTED, ServiceRequest.Status.CANCELLED);

    /**
     * ServiceNow-style work queue: open assigned items per agent across all
     * four ITSM ticket types. Incidents/problems/changes attribute by direct
     * assignee; service requests attribute to the agent holding an open
     * fulfillment task on them. Deleted tickets are excluded everywhere.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> agentWorkload(UUID orgId) {
        Map<UUID, Map<String, Object>> byAgent = new LinkedHashMap<>();
        Map<UUID, UUID> incidentOwner = new HashMap<>();
        Map<UUID, UUID> requestOwner = new HashMap<>();

        collectWorkload(orgId, byAgent,
                "SELECT a.id, COALESCE(a.displayName, a.email), i.id FROM Incident i JOIN i.assignee a " +
                        "WHERE i.orgId = :org AND i.deletedAt IS NULL AND i.status IN :statuses",
                WORKLOAD_INCIDENT_STATUSES, "incidents", incidentOwner);
        collectWorkload(orgId, byAgent,
                "SELECT a.id, COALESCE(a.displayName, a.email), p.id FROM Problem p JOIN p.assignee a " +
                        "WHERE p.orgId = :org AND p.deletedAt IS NULL AND p.status IN :statuses",
                WORKLOAD_PROBLEM_STATUSES, "problems", null);
        collectWorkload(orgId, byAgent,
                "SELECT a.id, COALESCE(a.displayName, a.email), c.id FROM ChangeRequest c JOIN c.assignee a " +
                        "WHERE c.orgId = :org AND c.deletedAt IS NULL AND c.status IN :statuses",
                WORKLOAD_CHANGE_STATUSES, "changes", null);

        List<Tuple> requestRows = entityManager.createQuery(
                "SELECT DISTINCT a.id, COALESCE(a.displayName, a.email), sr.id FROM FulfillmentTask ft " +
                        "JOIN ft.assignee a JOIN ft.serviceRequest sr " +
                        "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                        "AND ft.status IN :statuses AND sr.status NOT IN :terminal",
                Tuple.class)
                .setParameter("org", orgId)
                .setParameter("statuses", WORKLOAD_TASK_STATUSES)
                .setParameter("terminal", TERMINAL_REQUEST_STATUSES)
                .getResultList();
        for (Tuple t : requestRows) {
            addWorkloadItem(byAgent, t, "serviceRequests", requestOwner);
        }

        // SLA health segments for the tickets that have SLA instances.
        Map<UUID, SlaInstance.BreachStatus> slaByIncident = slaStatusIndex("incident", incidentOwner.keySet());
        Map<UUID, SlaInstance.BreachStatus> slaByRequest = slaStatusIndex("serviceRequest", requestOwner.keySet());
        for (Map.Entry<UUID, UUID> e : incidentOwner.entrySet()) {
            addSlaSegment(byAgent.get(e.getValue()), slaByIncident.get(e.getKey()));
        }
        for (Map.Entry<UUID, UUID> e : requestOwner.entrySet()) {
            addSlaSegment(byAgent.get(e.getValue()), slaByRequest.get(e.getKey()));
        }

        List<Map<String, Object>> result = new ArrayList<>(byAgent.values());
        result.sort((a, b) -> Long.compare((Long) b.get("openCount"), (Long) a.get("openCount")));
        return result;
    }

    private void collectWorkload(UUID orgId, Map<UUID, Map<String, Object>> byAgent,
                                 String jpql, List<? extends Enum<?>> statuses,
                                 String field, Map<UUID, UUID> ownerIndex) {
        List<Tuple> rows = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("org", orgId)
                .setParameter("statuses", statuses)
                .getResultList();
        for (Tuple t : rows) {
            addWorkloadItem(byAgent, t, field, ownerIndex);
        }
    }

    private void addWorkloadItem(Map<UUID, Map<String, Object>> byAgent, Tuple t,
                                 String field, Map<UUID, UUID> ownerIndex) {
        UUID agentId = t.get(0, UUID.class);
        String name = t.get(1, String.class);
        UUID ticketId = t.get(2, UUID.class);
        Map<String, Object> row = byAgent.computeIfAbsent(agentId, k -> {
            Map<String, Object> r = new HashMap<>();
            r.put("agentId", agentId);
            r.put("agentName", name);
            r.put("openCount", 0L);
            r.put("incidents", 0L);
            r.put("serviceRequests", 0L);
            r.put("problems", 0L);
            r.put("changes", 0L);
            r.put("onTrack", 0L);
            r.put("atRisk", 0L);
            r.put("breached", 0L);
            r.put("noSla", 0L);
            return r;
        });
        row.put(field, ((Long) row.get(field)) + 1);
        row.put("openCount", ((Long) row.get("openCount")) + 1);
        if (ownerIndex != null) {
            ownerIndex.put(ticketId, agentId);
        }
    }

    private void addSlaSegment(Map<String, Object> row, SlaInstance.BreachStatus status) {
        if (row == null) {
            return;
        }
        String key = status == null ? "noSla"
                : switch (status) {
                    case ON_TRACK -> "onTrack";
                    case AT_RISK -> "atRisk";
                    case BREACHED -> "breached";
                };
        row.put(key, ((Long) row.get(key)) + 1);
    }

    /**
     * One agent's full open queue across all four ticket types — the drill-down
     * behind "Workload per Agent". Mirrors agentWorkload attribution:
     * incidents/problems/changes by direct assignee, service requests via an
     * open fulfillment-task assignment.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> agentQueue(UUID orgId, UUID agentId) {
        List<Incident> incidents = entityManager.createQuery(
                "SELECT i FROM Incident i JOIN i.assignee a WHERE i.orgId = :org AND i.deletedAt IS NULL " +
                        "AND a.id = :agent AND i.status IN :statuses ORDER BY i.createdAt", Incident.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("statuses", WORKLOAD_INCIDENT_STATUSES).getResultList();
        List<Problem> problems = entityManager.createQuery(
                "SELECT p FROM Problem p JOIN p.assignee a WHERE p.orgId = :org AND p.deletedAt IS NULL " +
                        "AND a.id = :agent AND p.status IN :statuses ORDER BY p.createdAt", Problem.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("statuses", WORKLOAD_PROBLEM_STATUSES).getResultList();
        List<ChangeRequest> changes = entityManager.createQuery(
                "SELECT c FROM ChangeRequest c JOIN c.assignee a WHERE c.orgId = :org AND c.deletedAt IS NULL " +
                        "AND a.id = :agent AND c.status IN :statuses ORDER BY c.createdAt", ChangeRequest.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("statuses", WORKLOAD_CHANGE_STATUSES).getResultList();
        List<ServiceRequest> requests = entityManager.createQuery(
                "SELECT DISTINCT sr FROM FulfillmentTask ft JOIN ft.assignee a JOIN ft.serviceRequest sr " +
                        "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                        "AND a.id = :agent AND ft.status IN :statuses AND sr.status NOT IN :terminal " +
                        "ORDER BY sr.createdAt", ServiceRequest.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("statuses", WORKLOAD_TASK_STATUSES)
                .setParameter("terminal", TERMINAL_REQUEST_STATUSES).getResultList();

        Map<UUID, SlaInstance.BreachStatus> slaInc = slaStatusIndex("incident",
                incidents.stream().map(Incident::getId).toList());
        Map<UUID, SlaInstance.BreachStatus> slaSr = slaStatusIndex("serviceRequest",
                requests.stream().map(ServiceRequest::getId).toList());
        Map<UUID, SlaInstance.BreachStatus> slaProb = slaStatusIndex("problem",
                problems.stream().map(Problem::getId).toList());
        Map<UUID, SlaInstance.BreachStatus> slaChg = slaStatusIndex("changeRequest",
                changes.stream().map(ChangeRequest::getId).toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("incidents", incidents.stream().map(i -> queueRow(i.getId(), "INC-" + i.getNumber(),
                i.getTitle(), i.getStatus().name(), i.getCreatedAt(), slaInc.get(i.getId()))).toList());
        out.put("serviceRequests", requests.stream().map(s -> queueRow(s.getId(), s.getNumber(),
                s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request",
                s.getStatus().name(), s.getCreatedAt(), slaSr.get(s.getId()))).toList());
        out.put("problems", problems.stream().map(p -> queueRow(p.getId(), p.getNumber(),
                p.getTitle(), p.getStatus().name(), p.getCreatedAt(), slaProb.get(p.getId()))).toList());
        out.put("changes", changes.stream().map(c -> queueRow(c.getId(), c.getNumber(),
                c.getTitle(), c.getStatus().name(), c.getCreatedAt(), slaChg.get(c.getId()))).toList());
        return out;
    }

    private Map<String, Object> queueRow(UUID id, String number, String title, String status,
                                         OffsetDateTime createdAt, SlaInstance.BreachStatus sla) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("number", number);
        row.put("title", title);
        row.put("status", status);
        row.put("createdAt", createdAt);
        row.put("slaStatus", sla == null ? null : sla.name());
        return row;
    }

    /**
     * Full worked-ticket list for an agent over a period — incidents/problems/
     * changes by assignee+created range, service requests via fulfillment-task
     * assignment in range. Same "worked" definition as the monthly report.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> agentPerformanceTickets(UUID orgId, UUID agentId,
                                                             OffsetDateTime from, OffsetDateTime to,
                                                             String entityType, String status) {
        List<Map<String, Object>> out = new ArrayList<>();
        boolean want = entityType == null || entityType.isBlank() ? true : false;

        if (want || "incident".equals(entityType)) {
            for (Incident i : incidentRepositoryFilter(orgId, agentId, from, to, status)) {
                out.add(workedRow("INCIDENT", i.getId(), "INC-" + i.getNumber(), i.getTitle(),
                        i.getStatus().name(), i.getCreatedAt(), i.getResolvedAt()));
            }
        }
        if (want || "service_request".equals(entityType)) {
            List<ServiceRequest> srs = entityManager.createQuery(
                    "SELECT DISTINCT sr FROM FulfillmentTask ft JOIN ft.assignee a JOIN ft.serviceRequest sr " +
                            "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                            "AND a.id = :agent AND ft.assignedAt >= :from AND ft.assignedAt < :to " +
                            "ORDER BY sr.createdAt DESC", ServiceRequest.class)
                    .setParameter("org", orgId).setParameter("agent", agentId)
                    .setParameter("from", from).setParameter("to", to).getResultList();
            for (ServiceRequest s : srs) {
                if (status != null && !status.isBlank() && !s.getStatus().name().equals(status)) continue;
                out.add(workedRow("SERVICE_REQUEST", s.getId(), s.getNumber(),
                        s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request",
                        s.getStatus().name(), s.getCreatedAt(), null));
            }
        }
        if (want || "problem".equals(entityType)) {
            for (Problem p : problemListFor(orgId, agentId, from, to, status)) {
                out.add(workedRow("PROBLEM", p.getId(), p.getNumber(), p.getTitle(),
                        p.getStatus().name(), p.getCreatedAt(), p.getResolvedAt()));
            }
        }
        if (want || "change".equals(entityType)) {
            for (ChangeRequest c : changeListFor(orgId, agentId, from, to, status)) {
                out.add(workedRow("CHANGE", c.getId(), c.getNumber(), c.getTitle(),
                        c.getStatus().name(), c.getCreatedAt(), null));
            }
        }
        out.sort((a, b) -> ((OffsetDateTime) b.get("createdAt")).compareTo((OffsetDateTime) a.get("createdAt")));
        return out;
    }

    private List<Incident> incidentRepositoryFilter(UUID orgId, UUID agentId,
                                                    OffsetDateTime from, OffsetDateTime to, String status) {
        List<Incident> all = entityManager.createQuery(
                "SELECT i FROM Incident i WHERE i.orgId = :org AND i.deletedAt IS NULL " +
                        "AND i.assignee.id = :agent AND i.createdAt >= :from AND i.createdAt < :to " +
                        "ORDER BY i.createdAt DESC", Incident.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("from", from).setParameter("to", to).getResultList();
        return status == null || status.isBlank() ? all
                : all.stream().filter(i -> i.getStatus().name().equals(status)).toList();
    }

    private List<Problem> problemListFor(UUID orgId, UUID agentId,
                                         OffsetDateTime from, OffsetDateTime to, String status) {
        List<Problem> all = entityManager.createQuery(
                "SELECT p FROM Problem p WHERE p.orgId = :org AND p.deletedAt IS NULL " +
                        "AND p.assignee.id = :agent AND p.createdAt >= :from AND p.createdAt < :to " +
                        "ORDER BY p.createdAt DESC", Problem.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("from", from).setParameter("to", to).getResultList();
        return status == null || status.isBlank() ? all
                : all.stream().filter(p -> p.getStatus().name().equals(status)).toList();
    }

    private List<ChangeRequest> changeListFor(UUID orgId, UUID agentId,
                                              OffsetDateTime from, OffsetDateTime to, String status) {
        List<ChangeRequest> all = entityManager.createQuery(
                "SELECT c FROM ChangeRequest c WHERE c.orgId = :org AND c.deletedAt IS NULL " +
                        "AND c.assignee.id = :agent AND c.createdAt >= :from AND c.createdAt < :to " +
                        "ORDER BY c.createdAt DESC", ChangeRequest.class)
                .setParameter("org", orgId).setParameter("agent", agentId)
                .setParameter("from", from).setParameter("to", to).getResultList();
        return status == null || status.isBlank() ? all
                : all.stream().filter(c -> c.getStatus().name().equals(status)).toList();
    }

    private Map<String, Object> workedRow(String type, UUID id, String number, String title,
                                          String status, OffsetDateTime createdAt, OffsetDateTime resolvedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("id", id);
        row.put("number", number);
        row.put("title", title);
        row.put("status", status);
        row.put("createdAt", createdAt);
        row.put("resolvedAt", resolvedAt);
        return row;
    }

    /**
     * ServiceNow-style service-request operations snapshot for admins: status
     * breakdown, oldest open items, and things needing attention.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> serviceRequestOps(UUID orgId) {
        Map<String, Long> byStatus = entityManager.createQuery(
                "SELECT s.status, COUNT(*) FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                        "GROUP BY s.status", Tuple.class)
                .setParameter("org", orgId).getResultList().stream()
                .collect(Collectors.toMap(t -> t.get(0).toString(), t -> t.get(1, Long.class),
                        (a, b) -> a, LinkedHashMap::new));

        List<ServiceRequest> oldestOpen = entityManager.createQuery(
                "SELECT s FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                        "AND s.status NOT IN :terminal ORDER BY s.createdAt", ServiceRequest.class)
                .setParameter("org", orgId).setParameter("terminal", TERMINAL_REQUEST_STATUSES)
                .setMaxResults(10).getResultList();

        long pendingApprovals = byStatus.getOrDefault("PENDING_APPROVAL", 0L);
        long needsReview = byStatus.getOrDefault("REJECTED_NEEDS_REVIEW", 0L);
        Long unassignedTasks = entityManager.createQuery(
                "SELECT COUNT(ft) FROM FulfillmentTask ft JOIN ft.serviceRequest sr " +
                        "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                        "AND ft.assignee IS NULL AND ft.status IN :statuses", Long.class)
                .setParameter("org", orgId).setParameter("statuses", WORKLOAD_TASK_STATUSES)
                .getSingleResult();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("byStatus", byStatus);
        out.put("oldestOpen", oldestOpen.stream().map(s -> queueRow(s.getId(), s.getNumber(),
                s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request",
                s.getStatus().name(), s.getCreatedAt(), null)).toList());
        Map<String, Object> attention = new LinkedHashMap<>();
        attention.put("pendingApprovals", pendingApprovals);
        attention.put("rejectedNeedsReview", needsReview);
        attention.put("unassignedTasks", unassignedTasks);
        out.put("needsAttention", attention);
        return out;
    }

    /**
     * Admin triage view: what genuinely needs attention right now across all
     * four ticket types.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> needsAttention(UUID orgId) {
        Map<String, Object> out = new LinkedHashMap<>();

        List<Incident> unassigned = entityManager.createQuery(
                "SELECT i FROM Incident i WHERE i.orgId = :org AND i.deletedAt IS NULL " +
                        "AND i.assignee IS NULL AND i.status IN :statuses ORDER BY i.createdAt",
                Incident.class)
                .setParameter("org", orgId).setParameter("statuses", WORKLOAD_INCIDENT_STATUSES)
                .setMaxResults(10).getResultList();
        out.put("unassignedIncidents", unassigned.stream().map(i -> queueRow(i.getId(),
                "INC-" + i.getNumber(), i.getTitle(), i.getStatus().name(), i.getCreatedAt(), null)).toList());

        // Open tickets whose SLA instance is breached — by entity type.
        List<Tuple> breached = entityManager.createQuery(
                "SELECT si.id, si.breachStatus FROM SlaInstance si WHERE si.orgId = :org " +
                        "AND si.breachStatus = :breached AND si.resolutionMetAt IS NULL", Tuple.class)
                .setParameter("org", orgId)
                .setParameter("breached", SlaInstance.BreachStatus.BREACHED).getResultList();
        out.put("breachedSlaCount", breached.size());

        List<ServiceRequest> needsReview = entityManager.createQuery(
                "SELECT s FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                        "AND s.status = :status ORDER BY s.createdAt", ServiceRequest.class)
                .setParameter("org", orgId)
                .setParameter("status", ServiceRequest.Status.REJECTED_NEEDS_REVIEW)
                .setMaxResults(10).getResultList();
        out.put("rejectedNeedsReview", needsReview.stream().map(s -> queueRow(s.getId(), s.getNumber(),
                s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request",
                s.getStatus().name(), s.getCreatedAt(), null)).toList());

        // Auto-escalated tickets still sitting unassigned — nobody picked them up.
        List<Incident> staleEscalations = entityManager.createQuery(
                "SELECT i FROM Incident i WHERE i.orgId = :org AND i.deletedAt IS NULL " +
                        "AND i.assignee IS NULL AND i.assignmentTeam IS NOT NULL " +
                        "AND i.status IN :statuses ORDER BY i.updatedAt", Incident.class)
                .setParameter("org", orgId).setParameter("statuses", WORKLOAD_INCIDENT_STATUSES)
                .setMaxResults(10).getResultList();
        out.put("escalationsAwaitingPickup", staleEscalations.stream().map(i -> queueRow(i.getId(),
                "INC-" + i.getNumber(), i.getTitle(), i.getStatus().name(), i.getCreatedAt(), null)).toList());

        Long pendingApprovals = entityManager.createQuery(
                "SELECT COUNT(s) FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                        "AND s.status = :status", Long.class)
                .setParameter("org", orgId)
                .setParameter("status", ServiceRequest.Status.PENDING_APPROVAL).getSingleResult();
        out.put("pendingApprovals", pendingApprovals);

        return out;
    }

    private Map<UUID, SlaInstance.BreachStatus> slaStatusIndex(String association, Collection<UUID> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery(
                "SELECT si." + association + ".id, si.breachStatus FROM SlaInstance si " +
                        "WHERE si." + association + ".id IN :ids", Tuple.class)
                .setParameter("ids", ticketIds)
                .getResultList()
                .stream()
                .collect(Collectors.toMap(
                        t -> t.get(0, UUID.class),
                        t -> t.get(1, SlaInstance.BreachStatus.class),
                        (a, b) -> a));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> slaComplianceMonthly(UUID orgId, int months, UUID mineUserId) {
        if (months < 1 || months > 60) {
            throw new IllegalArgumentException("months must be between 1 and 60");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC).minusMonths(months).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SlaInstance> root = cq.from(SlaInstance.class);
        cq.multiselect(root.get("createdAt"), root.get("resolutionDueAt"), root.get("resolutionMetAt"));
        cq.where(
                cb.and(baseSlaInstancePredicates(cb, root, orgId, mineUserId)),
                cb.greaterThanOrEqualTo(root.get("createdAt"), start));

        List<Tuple> rows = entityManager.createQuery(cq).getResultList();

        record MonthlySla(long total, long breached) {}

        Map<YearMonth, MonthlySla> byMonth = new TreeMap<>();
        for (Tuple t : rows) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            OffsetDateTime resolutionDueAt = t.get(1, OffsetDateTime.class);
            OffsetDateTime resolutionMetAt = t.get(2, OffsetDateTime.class);
            if (createdAt == null) continue;
            YearMonth month = YearMonth.from(createdAt);
            MonthlySla current = byMonth.getOrDefault(month, new MonthlySla(0L, 0L));
            boolean isBreached = isBreachedAtDue(resolutionDueAt, resolutionMetAt, OffsetDateTime.now());
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
    public Map<String, Object> slaComplianceOverall(UUID orgId, UUID mineUserId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<SlaInstance> totalRoot = total.from(SlaInstance.class);
        total.select(cb.count(totalRoot));
        total.where(baseSlaInstancePredicates(cb, totalRoot, orgId, mineUserId));
        long totalCount = entityManager.createQuery(total).getSingleResult();

        CriteriaQuery<Long> breached = cb.createQuery(Long.class);
        Root<SlaInstance> breachRoot = breached.from(SlaInstance.class);
        breached.select(cb.count(breachRoot));
        OffsetDateTime now = OffsetDateTime.now();
        breached.where(
                cb.and(baseSlaInstancePredicates(cb, breachRoot, orgId, mineUserId)),
                cb.isNotNull(breachRoot.get("resolutionDueAt")),
                cb.or(
                        cb.and(
                                cb.isNotNull(breachRoot.get("resolutionMetAt")),
                                cb.greaterThan(breachRoot.get("resolutionMetAt"), breachRoot.get("resolutionDueAt"))),
                        cb.and(
                                cb.isNull(breachRoot.get("resolutionMetAt")),
                                cb.lessThan(breachRoot.get("resolutionDueAt"), now))));
        long breachedCount = entityManager.createQuery(breached).getSingleResult();

        double compliance = totalCount == 0 ? 100.0
                : ((totalCount - breachedCount) * 100.0 / totalCount);

        return Map.of(
                "total", totalCount,
                "breached", breachedCount,
                "compliancePercent", Math.round(compliance * 100.0) / 100.0);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<TicketsByLocationResponse> ticketsByLocation(UUID orgId, String status, OffsetDateTime from, OffsetDateTime to) {
        Set<Incident.Status> incidentStatuses = resolveIncidentStatuses(status);
        Set<ServiceRequest.Status> srStatuses = resolveServiceRequestStatuses(status);

        Map<UUID, LocationStats> byLocation = new LinkedHashMap<>();

        // Seed all active locations so locations with zero activity still appear.
        entityManager.createQuery(
                        "SELECT l FROM Location l WHERE l.orgId = :orgId AND l.deletedAt IS NULL ORDER BY l.name",
                        Location.class)
                .setParameter("orgId", orgId)
                .getResultStream()
                .forEach(l -> byLocation.computeIfAbsent(l.getId(), k -> new LocationStats(l.getId(), l.getName())));

        collectIncidentStats(byLocation, orgId, incidentStatuses, from, to);
        collectServiceRequestStats(byLocation, orgId, srStatuses, from, to);
        collectBreachedStats(byLocation, orgId, incidentStatuses, srStatuses, from, to);

        return byLocation.values().stream()
                .filter(s -> s.totalOpen() > 0 || s.resolved > 0 || s.breached > 0)
                .map(LocationStats::toResponse)
                .sorted(Comparator.comparingLong(TicketsByLocationResponse::totalOpen).reversed()
                        .thenComparing(TicketsByLocationResponse::locationName))
                .collect(Collectors.toList());
    }

    private Set<Incident.Status> resolveIncidentStatuses(String status) {
        if (status == null || status.isBlank() || "OPEN".equalsIgnoreCase(status)) {
            return Set.of(Incident.Status.NEW, Incident.Status.IN_PROGRESS,
                    Incident.Status.ON_HOLD, Incident.Status.REOPENED, Incident.Status.WAITING_ON_CUSTOMER);
        }
        if ("ALL".equalsIgnoreCase(status)) {
            return Set.of(Incident.Status.values());
        }
        return parseEnumSet(Incident.Status.class, status);
    }

    private Set<ServiceRequest.Status> resolveServiceRequestStatuses(String status) {
        if (status == null || status.isBlank() || "OPEN".equalsIgnoreCase(status)) {
            return Set.of(ServiceRequest.Status.SUBMITTED, ServiceRequest.Status.PENDING_APPROVAL,
                    ServiceRequest.Status.APPROVED, ServiceRequest.Status.IN_FULFILLMENT);
        }
        if ("ALL".equalsIgnoreCase(status)) {
            return Set.of(ServiceRequest.Status.values());
        }
        return parseEnumSet(ServiceRequest.Status.class, status);
    }

    private <E extends Enum<E>> Set<E> parseEnumSet(Class<E> clazz, String csv) {
        Set<E> result = EnumSet.noneOf(clazz);
        for (String part : csv.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) continue;
            try {
                result.add(Enum.valueOf(clazz, s.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result.isEmpty() ? EnumSet.allOf(clazz) : result;
    }

    private void collectIncidentStats(Map<UUID, LocationStats> byLocation, UUID orgId,
                                      Set<Incident.Status> statuses, OffsetDateTime from, OffsetDateTime to) {
        List<String> resolvedConditions = new ArrayList<>();
        if (from != null) resolvedConditions.add("i.resolvedAt >= :from");
        if (to != null) resolvedConditions.add("i.resolvedAt <= :to");

        StringBuilder jpql = new StringBuilder(
                "SELECT l.id, COALESCE(l.name, 'Unassigned'), COUNT(i), MIN(i.createdAt), " +
                        "SUM(CASE WHEN i.resolvedAt IS NOT NULL");
        if (!resolvedConditions.isEmpty()) {
            jpql.append(" AND (").append(String.join(" AND ", resolvedConditions)).append(")");
        }
        jpql.append(" THEN 1 ELSE 0 END) " +
                "FROM Incident i LEFT JOIN i.location l " +
                "WHERE i.orgId = :orgId AND i.deletedAt IS NULL AND i.status IN :statuses");
        if (from != null) jpql.append(" AND i.createdAt >= :from");
        if (to != null) jpql.append(" AND i.createdAt <= :to");
        jpql.append(" GROUP BY l.id, l.name");

        TypedQuery<Tuple> q = entityManager.createQuery(jpql.toString(), Tuple.class)
                .setParameter("orgId", orgId)
                .setParameter("statuses", statuses);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);

        for (Tuple t : q.getResultList()) {
            UUID id = t.get(0, UUID.class);
            String name = t.get(1, String.class);
            long count = t.get(2, Long.class);
            OffsetDateTime oldest = t.get(3, OffsetDateTime.class);
            long resolved = t.get(4, Long.class);
            LocationStats s = byLocation.computeIfAbsent(id, k -> new LocationStats(id, name));
            s.openIncidents += count;
            s.resolved += resolved;
            s.updateOldest(oldest);
        }
    }

    private void collectServiceRequestStats(Map<UUID, LocationStats> byLocation, UUID orgId,
                                            Set<ServiceRequest.Status> statuses, OffsetDateTime from, OffsetDateTime to) {
        List<String> fulfilledConditions = new ArrayList<>();
        if (from != null) fulfilledConditions.add("sr.createdAt >= :from");
        if (to != null) fulfilledConditions.add("sr.createdAt <= :to");

        StringBuilder jpql = new StringBuilder(
                "SELECT l.id, COALESCE(l.name, 'Unassigned'), COUNT(sr), MIN(sr.createdAt), " +
                        "SUM(CASE WHEN sr.status = :fulfilled");
        if (!fulfilledConditions.isEmpty()) {
            jpql.append(" AND (").append(String.join(" AND ", fulfilledConditions)).append(")");
        }
        jpql.append(" THEN 1 ELSE 0 END) " +
                "FROM ServiceRequest sr LEFT JOIN sr.location l " +
                "WHERE sr.orgId = :orgId AND sr.deletedAt IS NULL AND sr.status IN :statuses");
        if (from != null) jpql.append(" AND sr.createdAt >= :from");
        if (to != null) jpql.append(" AND sr.createdAt <= :to");
        jpql.append(" GROUP BY l.id, l.name");

        TypedQuery<Tuple> q = entityManager.createQuery(jpql.toString(), Tuple.class)
                .setParameter("orgId", orgId)
                .setParameter("statuses", statuses)
                .setParameter("fulfilled", ServiceRequest.Status.FULFILLED);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);

        for (Tuple t : q.getResultList()) {
            UUID id = t.get(0, UUID.class);
            String name = t.get(1, String.class);
            long count = t.get(2, Long.class);
            OffsetDateTime oldest = t.get(3, OffsetDateTime.class);
            long resolved = t.get(4, Long.class);
            LocationStats s = byLocation.computeIfAbsent(id, k -> new LocationStats(id, name));
            s.openServiceRequests += count;
            s.resolved += resolved;
            s.updateOldest(oldest);
        }
    }

    private void collectBreachedStats(Map<UUID, LocationStats> byLocation, UUID orgId,
                                      Set<Incident.Status> incidentStatuses,
                                      Set<ServiceRequest.Status> srStatuses,
                                      OffsetDateTime from, OffsetDateTime to) {
        // Incident breaches
        StringBuilder jpql = new StringBuilder(
                "SELECT i.location.id, COUNT(si) " +
                        "FROM SlaInstance si JOIN si.incident i " +
                        "WHERE si.breachStatus = :breached AND i.orgId = :orgId AND i.deletedAt IS NULL AND i.status IN :statuses");
        if (from != null) jpql.append(" AND i.createdAt >= :from");
        if (to != null) jpql.append(" AND i.createdAt <= :to");
        jpql.append(" GROUP BY i.location.id");

        TypedQuery<Tuple> q = entityManager.createQuery(jpql.toString(), Tuple.class)
                .setParameter("breached", SlaInstance.BreachStatus.BREACHED)
                .setParameter("orgId", orgId)
                .setParameter("statuses", incidentStatuses);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);

        for (Tuple t : q.getResultList()) {
            UUID id = t.get(0, UUID.class);
            long count = t.get(1, Long.class);
            byLocation.computeIfAbsent(id, k -> new LocationStats(id, "Unassigned")).breached += count;
        }
    }

    private static final class LocationStats {
        final UUID locationId;
        String locationName;
        long openIncidents;
        long openServiceRequests;
        long resolved;
        long breached;
        OffsetDateTime oldestOpen;

        LocationStats(UUID locationId, String locationName) {
            this.locationId = locationId;
            this.locationName = locationName;
        }

        long totalOpen() {
            return openIncidents + openServiceRequests;
        }

        void updateOldest(OffsetDateTime createdAt) {
            if (createdAt == null) return;
            if (oldestOpen == null || createdAt.isBefore(oldestOpen)) {
                oldestOpen = createdAt;
            }
        }

        Integer oldestOpenDays() {
            if (oldestOpen == null) return null;
            long days = ChronoUnit.DAYS.between(oldestOpen, OffsetDateTime.now());
            return days < 0 ? 0 : (int) days;
        }

        TicketsByLocationResponse toResponse() {
            return new TicketsByLocationResponse(
                    locationId,
                    locationName,
                    totalOpen(),
                    openIncidents,
                    openServiceRequests,
                    oldestOpenDays(),
                    resolved,
                    breached);
        }
    }

    /**
     * Jira-style sprint velocity: story points committed vs completed per
     * sprint (issue counts kept alongside for context). Only ACTIVE and
     * COMPLETED sprints are included — PLANNING sprints have no committed work
     * yet and would only produce empty bars. Ordered most recent first.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> sprintVelocity(UUID orgId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Issue> root = cq.from(Issue.class);
        Join<Issue, Sprint> sprint = root.join("sprint");
        Join<Issue, WorkflowStatus> workflowStatus = root.join("workflowStatus");

        Expression<Integer> points = cb.coalesce(root.get("storyPoints"), 0);
        Predicate done = cb.equal(workflowStatus.get("category"), WorkflowStatus.Category.DONE);

        cq.multiselect(
                sprint.get("name"),
                sprint.get("status"),
                sprint.get("endDate"),
                sprint.get("project").get("name"),
                cb.count(root),
                cb.sum(cb.<Long>selectCase().when(done, 1L).otherwise(0L)),
                cb.sum(points),
                cb.sum(cb.<Integer>selectCase().when(done, points).otherwise(0)));

        cq.where(
                cb.equal(root.get("orgId"), orgId),
                cb.isNull(root.get("deletedAt")),
                cb.isNull(sprint.get("deletedAt")),
                sprint.get("status").in(Sprint.Status.ACTIVE, Sprint.Status.COMPLETED));
        cq.groupBy(sprint.get("id"), sprint.get("name"), sprint.get("status"),
                sprint.get("endDate"), sprint.get("project").get("name"));
        cq.orderBy(cb.desc(sprint.get("startDate")));

        return entityManager.createQuery(cq)
                .getResultList()
                .stream()
                .map(t -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("sprintName", t.get(0, String.class));
                    row.put("sprintStatus", t.get(1, Sprint.Status.class).name());
                    row.put("endDate", t.get(2, OffsetDateTime.class));
                    row.put("projectName", t.get(3, String.class));
                    row.put("committed", t.get(4, Long.class));
                    row.put("completed", t.get(5, Long.class));
                    row.put("committedPoints", t.get(6, Long.class));
                    row.put("completedPoints", t.get(7, Long.class));
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** First-phase standard reports. */

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> incidentsByCategory(UUID orgId) {
        String jpql = """
                SELECT COALESCE(c.name, 'Unassigned'), COUNT(i)
                FROM Incident i
                LEFT JOIN i.category c
                WHERE i.orgId = :orgId
                  AND i.deletedAt IS NULL
                  AND i.status IN :statuses
                GROUP BY c.name
                """;
        List<Incident.Status> statuses = List.of(
                Incident.Status.NEW, Incident.Status.IN_PROGRESS,
                Incident.Status.ON_HOLD, Incident.Status.REOPENED, Incident.Status.WAITING_ON_CUSTOMER);
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("orgId", orgId)
                .setParameter("statuses", statuses);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            Map<String, Object> row = new HashMap<>();
            row.put("group", t.get(0, String.class));
            row.put("count", t.get(1, Long.class));
            rows.add(row);
        }
        return rows;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> requestsByCatalogItem(UUID orgId) {
        String jpql = """
                SELECT COALESCE(ci.name, 'Unknown'), COUNT(sr)
                FROM ServiceRequest sr
                LEFT JOIN sr.catalogItem ci
                WHERE sr.orgId = :orgId
                  AND sr.deletedAt IS NULL
                GROUP BY ci.name
                """;
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("orgId", orgId);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            Map<String, Object> row = new HashMap<>();
            row.put("group", t.get(0, String.class));
            row.put("count", t.get(1, Long.class));
            rows.add(row);
        }
        return rows;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> slaComplianceByPriority(UUID orgId) {
        String jpql = """
                SELECT COALESCE(p.name, 'Unassigned'),
                       COUNT(si),
                       SUM(CASE WHEN si.breachStatus = :breached THEN 1 ELSE 0 END)
                FROM SlaInstance si
                LEFT JOIN si.incident i
                LEFT JOIN i.priority p
                WHERE i.orgId = :orgId
                  AND i.deletedAt IS NULL
                  AND si.resolutionDueAt IS NOT NULL
                GROUP BY p.name
                """;
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("orgId", orgId)
                .setParameter("breached", SlaInstance.BreachStatus.BREACHED);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            String priority = t.get(0, String.class);
            Long total = t.get(1, Long.class);
            Long breached = t.get(2, Long.class);
            double compliance = total == null || total == 0 ? 100.0
                    : ((total - breached) * 100.0 / total);
            Map<String, Object> row = new HashMap<>();
            row.put("priority", priority);
            row.put("total", total);
            row.put("breached", breached);
            row.put("compliancePercent", Math.round(compliance * 100.0) / 100.0);
            rows.add(row);
        }
        return rows;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> pendingApprovalsBacklog(UUID orgId) {
        String jpql = """
                SELECT COALESCE(a.displayName, 'Unassigned'),
                       COUNT(sr),
                       MIN(sr.createdAt)
                FROM ServiceRequest sr
                LEFT JOIN sr.approver a
                WHERE sr.orgId = :orgId
                  AND sr.deletedAt IS NULL
                  AND sr.status = :status
                GROUP BY a.displayName
                """;
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("orgId", orgId)
                .setParameter("status", ServiceRequest.Status.PENDING_APPROVAL);
        List<Map<String, Object>> rows = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (Tuple t : q.getResultList()) {
            String approver = t.get(0, String.class);
            Long count = t.get(1, Long.class);
            OffsetDateTime oldest = t.get(2, OffsetDateTime.class);
            int oldestDays = oldest == null ? 0
                    : (int) Math.max(0, ChronoUnit.DAYS.between(oldest, now));
            Map<String, Object> row = new HashMap<>();
            row.put("approver", approver);
            row.put("count", count);
            row.put("oldestDays", oldestDays);
            rows.add(row);
        }
        return rows;
    }

    /**
     * Monthly created-vs-closed incident volume, including legacy imports
     * (they are real historical volume). Months are YYYY-MM buckets in UTC.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> ticketsMonthly(UUID orgId, int months, UUID mineUserId) {
        if (months < 1 || months > 120) {
            throw new IllegalArgumentException("months must be between 1 and 120");
        }
        OffsetDateTime start = YearMonth.now(ZoneOffset.UTC).minusMonths(months - 1L)
                .atDay(1).atStartOfDay().atOffset(ZoneOffset.UTC);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<Incident> root = cq.from(Incident.class);
        cq.multiselect(root.get("createdAt"), root.get("closedAt"));
        List<Predicate> predicates = new ArrayList<>(List.of(baseIncidentPredicates(cb, root, orgId, mineUserId)));
        predicates.add(cb.or(
                cb.greaterThanOrEqualTo(root.get("createdAt"), start),
                cb.greaterThanOrEqualTo(root.get("closedAt"), start)));
        cq.where(predicates.toArray(new Predicate[0]));

        Map<YearMonth, long[]> byMonth = new TreeMap<>();
        for (Tuple t : entityManager.createQuery(cq).getResultList()) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            OffsetDateTime closedAt = t.get(1, OffsetDateTime.class);
            if (createdAt != null) {
                YearMonth m = YearMonth.from(createdAt.atZoneSameInstant(ZoneOffset.UTC));
                if (!m.isBefore(YearMonth.from(start))) {
                    byMonth.computeIfAbsent(m, k -> new long[2])[0]++;
                }
            }
            if (closedAt != null) {
                YearMonth m = YearMonth.from(closedAt.atZoneSameInstant(ZoneOffset.UTC));
                if (!m.isBefore(YearMonth.from(start))) {
                    byMonth.computeIfAbsent(m, k -> new long[2])[1]++;
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        YearMonth cursor = YearMonth.from(start);
        for (int i = 0; i < months; i++) {
            long[] counts = byMonth.getOrDefault(cursor, new long[2]);
            result.add(Map.of("month", cursor.toString(), "created", counts[0], "closed", counts[1]));
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    /**
     * Legacy-import vs natively-created incident split — totals plus a
     * per-category breakdown. Keeps imported volume visible without letting
     * it distort live SLA metrics (legacy rows never have SlaInstance records).
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> legacySplit(UUID orgId, UUID mineUserId) {
        String mineClause = mineUserId != null ? " AND i.assignee.id = :mineUserId" : "";

        String totalsJpql = """
                SELECT i.legacyImport, COUNT(i)
                FROM Incident i
                WHERE i.orgId = :orgId AND i.deletedAt IS NULL
                """ + mineClause + " GROUP BY i.legacyImport";
        TypedQuery<Tuple> totalsQuery = entityManager.createQuery(totalsJpql, Tuple.class)
                .setParameter("orgId", orgId);
        if (mineUserId != null) totalsQuery.setParameter("mineUserId", mineUserId);

        long legacy = 0, current = 0;
        for (Tuple t : totalsQuery.getResultList()) {
            boolean isLegacy = Boolean.TRUE.equals(t.get(0, Boolean.class));
            long count = t.get(1, Long.class);
            if (isLegacy) legacy += count; else current += count;
        }

        String byCategoryJpql = """
                SELECT COALESCE(c.name, 'Uncategorized'), i.legacyImport, COUNT(i)
                FROM Incident i
                LEFT JOIN i.category c
                WHERE i.orgId = :orgId AND i.deletedAt IS NULL
                """ + mineClause + " GROUP BY c.name, i.legacyImport";
        TypedQuery<Tuple> catQuery = entityManager.createQuery(byCategoryJpql, Tuple.class)
                .setParameter("orgId", orgId);
        if (mineUserId != null) catQuery.setParameter("mineUserId", mineUserId);

        Map<String, long[]> byCategory = new TreeMap<>();
        for (Tuple t : catQuery.getResultList()) {
            String category = t.get(0, String.class);
            boolean isLegacy = Boolean.TRUE.equals(t.get(1, Boolean.class));
            long count = t.get(2, Long.class);
            byCategory.computeIfAbsent(category, k -> new long[2])[isLegacy ? 0 : 1] += count;
        }
        List<Map<String, Object>> categories = byCategory.entrySet().stream()
                .map(e -> Map.<String, Object>of("category", e.getKey(), "legacy", e.getValue()[0], "current", e.getValue()[1]))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("legacy", legacy);
        out.put("current", current);
        out.put("byCategory", categories);
        return out;
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
