package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.api.reporting.ReportMetadataResponse;
import com.alignedcardio.itsm.api.reporting.TicketsByLocationResponse;
import com.alignedcardio.itsm.entity.*;
import com.alignedcardio.itsm.service.SupportTiers;
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
            "change", Set.of("orgId", "status", "assignee", "requestedBy", "changeType", "risk", "createdAt", "plannedStart"),
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

        if (request.isDetailed()) {
            return detailedQuery(orgId, entityClass, allowedFields, request, mineUserId);
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(entityClass);
        cq.where(buildQueryPredicates(cb, root, orgId, request, mineUserId).toArray(new Predicate[0]));

        boolean hasGroupBy = request.groupBy() != null && !request.groupBy().isBlank();

        Expression<?> keyExpr = null;
        if (hasGroupBy) {
            Path<?> rawPath = root.get(request.groupBy());
            Expression<?> groupExpr;
            if (BaseEntity.class.isAssignableFrom(rawPath.getJavaType())) {
                Join<?, ?> join = root.join(request.groupBy(), JoinType.LEFT);
                // AppUser has displayName, not name.
                Path<String> namePath = AppUser.class.isAssignableFrom(rawPath.getJavaType())
                        ? join.get("displayName")
                        : join.get("name");
                groupExpr = cb.coalesce(namePath, cb.literal("Unassigned"));
                // The raw id is the drill-down key — a display name can't be
                // re-filtered on an entity-valued field.
                keyExpr = join.get("id");
            } else {
                groupExpr = rawPath;
                keyExpr = rawPath;
            }
            cq.groupBy(groupExpr, keyExpr);
            cq.multiselect(groupExpr, keyExpr, cb.count(root));
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
                Object key = t.get(1);
                row.put("groupKey", key == null ? null : key.toString());
                row.put("count", t.get(2));
            } else {
                row.put("group", null);
                row.put("count", t.get(0));
            }
            rows.add(row);
        }

        return new AdHocQueryResponse(orgId, request.entity(), request.groupBy(), rows);
    }

    /** Shared where-clause for grouped and detailed modes. */
    private List<Predicate> buildQueryPredicates(CriteriaBuilder cb, Root<?> root, UUID orgId,
                                               AdHocQueryRequest request, UUID mineUserId) {
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
                predicates.add(buildPredicate(cb, root, filter, ENTITY_WHITELIST.get(request.entity())));
            }
        }
        return predicates;
    }

    /**
     * Detailed mode: the same predicates, but returning real entity rows with
     * display-ready fields and a detailUrl for click-through. Paginated.
     */
    private AdHocQueryResponse detailedQuery(UUID orgId, Class<?> entityClass, Set<String> allowedFields,
                                             AdHocQueryRequest request, UUID mineUserId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> countQ = cb.createQuery(Long.class);
        Root<?> countRoot = countQ.from(entityClass);
        countQ.select(cb.count(countRoot))
                .where(buildQueryPredicates(cb, countRoot, orgId, request, mineUserId).toArray(new Predicate[0]));
        long total = entityManager.createQuery(countQ).getSingleResult();

        CriteriaQuery<Object> selectQ = cb.createQuery(Object.class);
        Root<?> root = selectQ.from(entityClass);
        selectQ.select(root)
                .where(buildQueryPredicates(cb, root, orgId, request, mineUserId).toArray(new Predicate[0]))
                .orderBy(cb.desc(root.get("createdAt")));

        int page = request.pageOrDefault();
        int pageSize = request.pageSizeOrDefault();
        List<?> entities = entityManager.createQuery(selectQ)
                .setFirstResult(page * pageSize)
                .setMaxResults(pageSize)
                .getResultList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object entity : entities) {
            rows.add(detailRow(request.entity(), entity));
        }
        return new AdHocQueryResponse(orgId, request.entity(), request.groupBy(), rows,
                total, page, pageSize);
    }

    /** Display-ready row for detailed-mode results, with a click-through URL. */
    private Map<String, Object> detailRow(String entity, Object o) {
        Map<String, Object> r = new LinkedHashMap<>();
        switch (entity) {
            case "incident" -> {
                Incident i = (Incident) o;
                r.put("id", i.getId());
                r.put("ref", "INC-" + i.getNumber());
                r.put("title", i.getTitle());
                r.put("status", i.getStatus() != null ? i.getStatus().name() : "");
                r.put("priority", i.getPriority() != null ? i.getPriority().getName() : "");
                r.put("category", i.getCategory() != null ? i.getCategory().getName() : "");
                r.put("location", i.getLocation() != null ? i.getLocation().getName() : "");
                r.put("assignee", i.getAssignee() != null ? i.getAssignee().getDisplayName() : "");
                r.put("requester", i.getRequester() != null ? i.getRequester().getDisplayName() : "");
                r.put("createdAt", i.getCreatedAt());
                r.put("resolvedAt", i.getResolvedAt());
                r.put("detailUrl", "/dashboard/incidents/" + i.getId());
            }
            case "service_request" -> {
                ServiceRequest s = (ServiceRequest) o;
                r.put("id", s.getId());
                r.put("ref", s.getNumber());
                r.put("title", s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request");
                r.put("status", s.getStatus() != null ? s.getStatus().name() : "");
                r.put("priority", s.getPriority() != null ? s.getPriority().getName() : "");
                r.put("location", s.getLocation() != null ? s.getLocation().getName() : "");
                r.put("requester", s.getRequester() != null ? s.getRequester().getDisplayName() : "");
                r.put("approver", s.getApprover() != null ? s.getApprover().getDisplayName() : "");
                r.put("createdAt", s.getCreatedAt());
                r.put("decidedAt", s.getDecidedAt());
                r.put("detailUrl", "/dashboard/service-requests/" + s.getId());
            }
            case "problem" -> {
                Problem p = (Problem) o;
                r.put("id", p.getId());
                r.put("ref", p.getNumber());
                r.put("title", p.getTitle());
                r.put("status", p.getStatus() != null ? p.getStatus().name() : "");
                r.put("assignee", p.getAssignee() != null ? p.getAssignee().getDisplayName() : "");
                r.put("createdAt", p.getCreatedAt());
                r.put("resolvedAt", p.getResolvedAt());
                r.put("detailUrl", "/dashboard/problems/" + p.getId());
            }
            case "change" -> {
                ChangeRequest c = (ChangeRequest) o;
                r.put("id", c.getId());
                r.put("ref", c.getNumber());
                r.put("title", c.getTitle());
                r.put("status", c.getStatus() != null ? c.getStatus().name() : "");
                r.put("changeType", c.getChangeType() != null ? c.getChangeType().name() : "");
                r.put("risk", c.getRisk() != null ? c.getRisk().name() : "");
                r.put("assignee", c.getAssignee() != null ? c.getAssignee().getDisplayName() : "");
                r.put("requestedBy", c.getRequestedBy() != null ? c.getRequestedBy().getDisplayName() : "");
                r.put("createdAt", c.getCreatedAt());
                r.put("detailUrl", "/dashboard/changes/" + c.getId());
            }
            case "issue" -> {
                Issue i = (Issue) o;
                r.put("id", i.getId());
                r.put("ref", i.getKey());
                r.put("title", i.getSummary());
                r.put("status", i.getWorkflowStatus() != null ? i.getWorkflowStatus().getName() : "");
                r.put("priority", i.getPriority() != null ? i.getPriority().name() : "");
                r.put("assignee", i.getAssignee() != null ? i.getAssignee().getDisplayName() : "");
                r.put("reporter", i.getReporter() != null ? i.getReporter().getDisplayName() : "");
                r.put("createdAt", i.getCreatedAt());
                r.put("detailUrl", i.getProject() != null
                        ? "/dashboard/projects/" + i.getProject().getId() + "/issues/" + i.getId()
                        : "/dashboard/projects");
            }
            default -> {
            }
        }
        return r;
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
        return ticketsSummary(orgId, mineUserId, null, null);
    }

    /**
     * Tickets summary. `from`/`to` scope the *activity* metrics (created-in-
     * range total, resolved-in-range) — snapshot counts (open, in-progress,
     * unassigned, open-by-type) are always current state.
     */
    public Map<String, Object> ticketsSummary(UUID orgId, UUID mineUserId,
                                              OffsetDateTime from, OffsetDateTime to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<Long> total = cb.createQuery(Long.class);
        Root<Incident> root = total.from(Incident.class);
        total.select(cb.count(root));
        List<Predicate> totalPreds = new ArrayList<>(List.of(baseIncidentPredicates(cb, root, orgId, mineUserId)));
        if (from != null) totalPreds.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        if (to != null) totalPreds.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
        total.where(totalPreds.toArray(new Predicate[0]));
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

        // Resolved-in-range — only meaningful when the caller scoped a period.
        Long resolvedInRange = null;
        if (from != null || to != null) {
            CriteriaQuery<Long> rr = cb.createQuery(Long.class);
            Root<Incident> rrRoot = rr.from(Incident.class);
            rr.select(cb.count(rrRoot));
            List<Predicate> rrPreds = new ArrayList<>(
                    List.of(baseIncidentPredicates(cb, rrRoot, orgId, mineUserId)));
            rrPreds.add(cb.isNotNull(rrRoot.get("resolvedAt")));
            rrPreds.add(cb.isFalse(rrRoot.get("legacyImport")));
            if (from != null) rrPreds.add(cb.greaterThanOrEqualTo(rrRoot.get("resolvedAt"), from));
            if (to != null) rrPreds.add(cb.lessThanOrEqualTo(rrRoot.get("resolvedAt"), to));
            rr.where(rrPreds.toArray(new Predicate[0]));
            resolvedInRange = entityManager.createQuery(rr).getSingleResult();
        }

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

        // Open work across all four ticket types — admins need cross-entity
        // visibility, not incidents-only.
        Map<String, Object> openByType = new LinkedHashMap<>();
        openByType.put("incidents", openCount);
        openByType.put("serviceRequests", entityManager.createQuery(
                "SELECT COUNT(s) FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL " +
                        "AND s.status NOT IN :terminal", Long.class)
                .setParameter("org", orgId)
                .setParameter("terminal", TERMINAL_REQUEST_STATUSES)
                .getSingleResult());
        openByType.put("problems", entityManager.createQuery(
                "SELECT COUNT(p) FROM Problem p WHERE p.orgId = :org AND p.deletedAt IS NULL " +
                        "AND p.status NOT IN :terminal", Long.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(Problem.Status.RESOLVED, Problem.Status.CLOSED))
                .getSingleResult());
        openByType.put("changes", entityManager.createQuery(
                "SELECT COUNT(c) FROM ChangeRequest c WHERE c.orgId = :org AND c.deletedAt IS NULL " +
                        "AND c.status NOT IN :terminal", Long.class)
                .setParameter("org", orgId)
                .setParameter("terminal", List.of(ChangeRequest.Status.COMPLETED, ChangeRequest.Status.FAILED,
                        ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED,
                        ChangeRequest.Status.CLOSED, ChangeRequest.Status.REJECTED))
                .getSingleResult());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", totalCount);
        result.put("open", openCount);
        result.put("inProgress", inProgressCount);
        result.put("resolvedToday", resolvedToday);
        result.put("unassigned", unassignedCount);
        result.put("openByType", openByType);
        if (resolvedInRange != null) {
            result.put("resolvedInRange", resolvedInRange);
        }
        return result;
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
        // Cancelled requests are withdrawn — not a fulfilled SLA. Counting them
        // as "met" would inflate compliance, so they're excluded entirely.
        predicates.add(cb.or(
                cb.isNull(serviceRequest.get("id")),
                cb.notEqual(serviceRequest.get("status"), ServiceRequest.Status.CANCELLED)));
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

        // JOIN FETCH avoids a lazy-load per instance (N+1): associations are
        // populated in the single query. All targets are ManyToOne, so no
        // MultipleBagFetchException risk.
        List<SlaInstance> instances = entityManager.createQuery(
                "SELECT si FROM SlaInstance si " +
                        "LEFT JOIN FETCH si.incident i " +
                        "LEFT JOIN FETCH i.assignee " +
                        "LEFT JOIN FETCH i.assignmentTeam " +
                        "LEFT JOIN FETCH si.serviceRequest sr " +
                        "LEFT JOIN FETCH sr.requester " +
                        "LEFT JOIN FETCH si.problem p " +
                        "LEFT JOIN FETCH p.assignee " +
                        "LEFT JOIN FETCH si.changeRequest c " +
                        "LEFT JOIN FETCH c.assignee " +
                        "WHERE si.orgId = :org AND si.createdAt >= :from " +
                        "AND (sr IS NULL OR sr.status <> :cancelled)",
                SlaInstance.class)
                .setParameter("org", orgId)
                .setParameter("from", from)
                .setParameter("cancelled", ServiceRequest.Status.CANCELLED)
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
     * SLA compliance grouped by a ticket dimension — agent (incident/problem/
     * change assignee, or service-request fulfillment-task assignees) or
     * location (incident/request location). Cancelled requests are excluded.
     * Powers the canned SLA-compliance templates; the generic query builder
     * can't express this because SLA instances aren't a whitelisted entity.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> slaComplianceByDimension(UUID orgId, String entity, String dimension) {
        if (!Set.of("incident", "service_request", "problem", "change").contains(entity)) {
            throw new IllegalArgumentException("Unknown entity for SLA dimension report: " + entity);
        }
        if (!Set.of("agent", "location").contains(dimension)) {
            throw new IllegalArgumentException("Unknown dimension: " + dimension);
        }
        String fk = switch (entity) {
            case "incident" -> "incident";
            case "service_request" -> "serviceRequest";
            case "problem" -> "problem";
            default -> "changeRequest";
        };

        String jpql = "SELECT si FROM SlaInstance si WHERE si.orgId = :org AND si." + fk + " IS NOT NULL";
        if ("service_request".equals(entity)) {
            jpql += " AND si.serviceRequest.status <> :cancelled";
        }
        TypedQuery<SlaInstance> q = entityManager.createQuery(jpql, SlaInstance.class)
                .setParameter("org", orgId);
        if ("service_request".equals(entity)) {
            q.setParameter("cancelled", ServiceRequest.Status.CANCELLED);
        }
        List<SlaInstance> instances = q.getResultList();

        // SR agent attribution: distinct fulfillment-task assignees per request.
        Map<UUID, List<String>> taskOwners = new HashMap<>();
        if ("service_request".equals(entity) && "agent".equals(dimension)) {
            List<UUID> ids = instances.stream()
                    .map(si -> si.getServiceRequest().getId()).toList();
            if (!ids.isEmpty()) {
                for (Tuple t : entityManager.createQuery(
                        "SELECT DISTINCT ft.serviceRequest.id, a.displayName FROM FulfillmentTask ft " +
                                "JOIN ft.assignee a WHERE ft.serviceRequest.id IN :ids AND ft.deletedAt IS NULL",
                        Tuple.class)
                        .setParameter("ids", ids).getResultList()) {
                    taskOwners.computeIfAbsent(t.get(0, UUID.class), k -> new ArrayList<>())
                            .add(t.get(1, String.class));
                }
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<String, long[]> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SlaInstance si : instances) {
            boolean breached = isBreachedAtDue(si.getResolutionDueAt(), si.getResolutionMetAt(), now);
            List<String> names = switch (entity) {
                case "incident" -> "agent".equals(dimension)
                        ? List.of(si.getIncident().getAssignee() != null
                                ? si.getIncident().getAssignee().getDisplayName() : "Unassigned")
                        : List.of(si.getIncident().getLocation() != null
                                ? si.getIncident().getLocation().getName() : "No location");
                case "service_request" -> "agent".equals(dimension)
                        ? taskOwners.getOrDefault(si.getServiceRequest().getId(), List.of("Unassigned"))
                        : List.of(si.getServiceRequest().getLocation() != null
                                ? si.getServiceRequest().getLocation().getName() : "No location");
                case "problem" -> "agent".equals(dimension)
                        ? List.of(si.getProblem().getAssignee() != null
                                ? si.getProblem().getAssignee().getDisplayName() : "Unassigned")
                        : List.of("No location");
                default -> "agent".equals(dimension)
                        ? List.of(si.getChangeRequest().getAssignee() != null
                                ? si.getChangeRequest().getAssignee().getDisplayName() : "Unassigned")
                        : List.of("No location");
            };
            for (String name : names) {
                long[] counts = byName.computeIfAbsent(name, k -> new long[2]);
                counts[0]++;
                if (breached) counts[1]++;
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, long[]> e : byName.entrySet()) {
            long total = e.getValue()[0];
            long breached = e.getValue()[1];
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", e.getKey());
            r.put("total", total);
            r.put("breached", breached);
            r.put("compliancePercent", Math.round((total - breached) * 10000.0 / total) / 100.0);
            rows.add(r);
        }
        return rows;
    }

    /**
     * "Tickets I Worked On": tickets the agent took an action on within the
     * range — status changes, assignments, escalations, task work — sourced
     * from audit_log (actorUserId). This is the true "worked" definition: the
     * previous version filtered by ticket creation date, so anything worked on
     * outside its creation window silently disappeared.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> agentPerformanceTickets(UUID orgId, List<UUID> agentIds,
                                                             OffsetDateTime from, OffsetDateTime to,
                                                             String entityType, String status) {
        // audit entityType → lower-case report entity key.
        Map<String, String> auditTypes = Map.of(
                "INCIDENT", "incident",
                "SERVICE_REQUEST", "service_request",
                "PROBLEM", "problem",
                "CHANGE_REQUEST", "change");
        List<String> wanted = auditTypes.entrySet().stream()
                .filter(e -> entityType == null || entityType.isBlank() || entityType.equals(e.getValue()))
                .map(Map.Entry::getKey).toList();

        // Distinct tickets the agent acted on in range + when they last acted.
        List<Tuple> auditRows = entityManager.createQuery(
                "SELECT a.entityType, a.entityId, MAX(a.createdAt) FROM AuditLog a " +
                        "WHERE a.orgId = :org AND a.actorUserId IN :agents " +
                        "AND a.createdAt >= :from AND a.createdAt < :to " +
                        "AND a.entityType IN :types " +
                        "GROUP BY a.entityType, a.entityId", Tuple.class)
                .setParameter("org", orgId).setParameter("agents", agentIds)
                .setParameter("from", from).setParameter("to", to)
                .setParameter("types", wanted)
                .getResultList();

        Map<String, Map<UUID, OffsetDateTime>> idsByType = new HashMap<>();
        for (Tuple t : auditRows) {
            String type = auditTypes.get(t.get(0, String.class));
            if (type == null) continue;
            idsByType.computeIfAbsent(type, k -> new HashMap<>())
                    .put(t.get(1, UUID.class), t.get(2, OffsetDateTime.class));
        }

        List<Map<String, Object>> out = new ArrayList<>();
        addWorked(out, "INCIDENT", idsByType.get("incident"), status,
                "SELECT i FROM Incident i WHERE i.orgId = :org AND i.deletedAt IS NULL AND i.id IN :ids",
                Incident.class, orgId,
                i -> workedRow("INCIDENT", i.getId(), "INC-" + i.getNumber(), i.getTitle(),
                        i.getStatus().name(), i.getCreatedAt(), i.getResolvedAt()));
        addWorked(out, "SERVICE_REQUEST", idsByType.get("service_request"), status,
                "SELECT s FROM ServiceRequest s WHERE s.orgId = :org AND s.deletedAt IS NULL AND s.id IN :ids",
                ServiceRequest.class, orgId,
                s -> workedRow("SERVICE_REQUEST", s.getId(), s.getNumber(),
                        s.getCatalogItem() != null ? s.getCatalogItem().getName() : "Service request",
                        s.getStatus().name(), s.getCreatedAt(), null));
        addWorked(out, "PROBLEM", idsByType.get("problem"), status,
                "SELECT p FROM Problem p WHERE p.orgId = :org AND p.deletedAt IS NULL AND p.id IN :ids",
                Problem.class, orgId,
                p -> workedRow("PROBLEM", p.getId(), p.getNumber(), p.getTitle(),
                        p.getStatus().name(), p.getCreatedAt(), p.getResolvedAt()));
        addWorked(out, "CHANGE", idsByType.get("change"), status,
                "SELECT c FROM ChangeRequest c WHERE c.orgId = :org AND c.deletedAt IS NULL AND c.id IN :ids",
                ChangeRequest.class, orgId,
                c -> workedRow("CHANGE", c.getId(), c.getNumber(), c.getTitle(),
                        c.getStatus().name(), c.getCreatedAt(), null));

        out.sort((a, b) -> {
            OffsetDateTime wa = (OffsetDateTime) a.get("workedAt");
            OffsetDateTime wb = (OffsetDateTime) b.get("workedAt");
            return wb.compareTo(wa);
        });
        return out;
    }

    private interface WorkedRowMapper<T> {
        Map<String, Object> map(T entity);
    }

    private <T> void addWorked(List<Map<String, Object>> out, String typeLabel,
                               Map<UUID, OffsetDateTime> idToWorkedAt, String status,
                               String jpql, Class<T> type, UUID orgId, WorkedRowMapper<T> mapper) {
        if (idToWorkedAt == null || idToWorkedAt.isEmpty()) return;
        List<T> entities = entityManager.createQuery(jpql, type)
                .setParameter("org", orgId).setParameter("ids", idToWorkedAt.keySet()).getResultList();
        for (T e : entities) {
            Map<String, Object> row = mapper.map(e);
            if (status != null && !status.isBlank() && !status.equals(row.get("status"))) continue;
            row.put("workedAt", idToWorkedAt.get(row.get("id")));
            out.add(row);
        }
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

        // Aging: how long open requests have been sitting, bucketed per status
        // ([0-1d, 2-3d, 4-7d, 8d+]).
        Set<ServiceRequest.Status> openStatuses = EnumSet.complementOf(
                EnumSet.copyOf(TERMINAL_REQUEST_STATUSES));
        OffsetDateTime now = OffsetDateTime.now();
        Map<ServiceRequest.Status, long[]> aging = new LinkedHashMap<>();
        for (ServiceRequest.Status s : openStatuses) aging.put(s, new long[4]);
        for (Tuple t : entityManager.createQuery(
                "SELECT s.status, s.createdAt FROM ServiceRequest s WHERE s.orgId = :org " +
                        "AND s.deletedAt IS NULL AND s.status IN :open", Tuple.class)
                .setParameter("org", orgId)
                .setParameter("open", openStatuses)
                .getResultList()) {
            OffsetDateTime created = t.get(1, OffsetDateTime.class);
            long days = created == null ? 0 : java.time.Duration.between(created, now).toDays();
            int bucket = days <= 1 ? 0 : days <= 3 ? 1 : days <= 7 ? 2 : 3;
            aging.get(t.get(0, ServiceRequest.Status.class))[bucket]++;
        }
        List<Map<String, Object>> agingRows = new ArrayList<>();
        for (Map.Entry<ServiceRequest.Status, long[]> e : aging.entrySet()) {
            long[] b = e.getValue();
            if (b[0] + b[1] + b[2] + b[3] == 0) continue;
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("status", e.getKey().name());
            r.put("age0to1", b[0]);
            r.put("age2to3", b[1]);
            r.put("age4to7", b[2]);
            r.put("age8plus", b[3]);
            agingRows.add(r);
        }

        // Open requests whose SLA clock is already at risk or breached.
        Long slaAtRisk = entityManager.createQuery(
                "SELECT COUNT(si) FROM SlaInstance si WHERE si.orgId = :org " +
                        "AND si.serviceRequest.deletedAt IS NULL " +
                        "AND si.serviceRequest.status NOT IN :terminal " +
                        "AND si.breachStatus IN :risk", Long.class)
                .setParameter("org", orgId)
                .setParameter("terminal", TERMINAL_REQUEST_STATUSES)
                .setParameter("risk", List.of(SlaInstance.BreachStatus.AT_RISK,
                        SlaInstance.BreachStatus.BREACHED))
                .getSingleResult();

        // Fulfillment tasks past their expected delivery date and still not
        // delivered — the same population the reminder job emails about.
        List<FulfillmentTask> overdue = entityManager.createQuery(
                "SELECT ft FROM FulfillmentTask ft JOIN ft.serviceRequest sr " +
                        "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                        "AND ft.expectedDeliveryDate IS NOT NULL AND ft.expectedDeliveryDate < :today " +
                        "AND ft.deliveredAt IS NULL AND ft.status <> :done " +
                        "ORDER BY ft.expectedDeliveryDate", FulfillmentTask.class)
                .setParameter("org", orgId)
                .setParameter("today", LocalDate.now())
                .setParameter("done", FulfillmentTask.Status.COMPLETED)
                .getResultList();
        Map<String, Object> overdueDeliveries = new LinkedHashMap<>();
        overdueDeliveries.put("count", overdue.size());
        overdue.stream().findFirst().ifPresent(ft -> {
            Map<String, Object> oldest = new LinkedHashMap<>();
            oldest.put("taskId", ft.getId());
            oldest.put("requestId", ft.getServiceRequest().getId());
            oldest.put("requestNumber", ft.getServiceRequest().getNumber());
            oldest.put("description", ft.getDescription());
            oldest.put("expectedDeliveryDate", ft.getExpectedDeliveryDate());
            overdueDeliveries.put("oldest", oldest);
        });

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
        out.put("aging", agingRows);
        out.put("slaAtRisk", slaAtRisk);
        out.put("overdueDeliveries", overdueDeliveries);
        out.put("approverBacklog", pendingApprovalsBacklog(orgId));
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

        // KB articles waiting on a publish/reject decision — governance
        // visibility, same "needs attention" theme.
        Long kbPending = entityManager.createQuery(
                "SELECT COUNT(a) FROM KbArticle a WHERE a.orgId = :org AND a.status = :st", Long.class)
                .setParameter("org", orgId)
                .setParameter("st", KbArticle.Status.PENDING_REVIEW)
                .getSingleResult();
        out.put("kbPendingReview", kbPending);

        // Unassigned fulfillment tasks — work sitting with no owner.
        Long unassignedTasks = entityManager.createQuery(
                "SELECT COUNT(ft) FROM FulfillmentTask ft JOIN ft.serviceRequest sr " +
                        "WHERE sr.orgId = :org AND sr.deletedAt IS NULL AND ft.deletedAt IS NULL " +
                        "AND ft.assignee IS NULL AND ft.status IN :statuses", Long.class)
                .setParameter("org", orgId)
                .setParameter("statuses", WORKLOAD_TASK_STATUSES)
                .getSingleResult();
        out.put("unassignedFulfillmentTasks", unassignedTasks);

        return out;
    }

    /**
     * SUPER_ADMIN config audit — silently-broken setup that produces runtime
     * failures or dead ends: empty support-tier teams, locations with no
     * approval manager, approval-gated catalog items with no fallback
     * approver, and SLA policies with no escalation tiers.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Map<String, Object> configHealth(UUID orgId) {
        Map<String, Object> out = new LinkedHashMap<>();

        List<String> emptyTeams = new ArrayList<>();
        for (UUID teamId : List.of(SupportTiers.L1_ID, SupportTiers.L2_ID,
                SupportTiers.L3_ID, SupportTiers.IT_FULFILLMENT_ID)) {
            List<Team> teams = entityManager.createQuery(
                    "SELECT t FROM Team t WHERE t.id = :id", Team.class)
                    .setParameter("id", teamId).getResultList();
            Long members = entityManager.createQuery(
                    "SELECT COUNT(m) FROM TeamMember m WHERE m.team.id = :id", Long.class)
                    .setParameter("id", teamId).getSingleResult();
            if (members == 0) {
                emptyTeams.add(teams.isEmpty() ? teamId.toString() : teams.get(0).getName());
            }
        }
        out.put("emptyTeams", emptyTeams);

        List<String> locationsNoApprover = entityManager.createQuery(
                "SELECT l.name FROM Location l WHERE l.orgId = :org AND l.deletedAt IS NULL " +
                        "AND l.approvalManager IS NULL", String.class)
                .setParameter("org", orgId).getResultList();
        out.put("locationsWithoutApprover", locationsNoApprover);

        // Catalog items where some form option requires approval but the item
        // has no fallback approver — combined with a manager-less location,
        // approval routing fails at submit time.
        List<CatalogItem> items = entityManager.createQuery(
                "SELECT c FROM CatalogItem c WHERE c.orgId = :org AND c.active = true",
                CatalogItem.class)
                .setParameter("org", orgId).getResultList();
        List<String> itemsNeedingApprover = new ArrayList<>();
        for (CatalogItem item : items) {
            if (item.getApprover() != null || item.getFormSchema() == null || !item.getFormSchema().isArray()) {
                continue;
            }
            for (com.fasterxml.jackson.databind.JsonNode field : item.getFormSchema()) {
                boolean gated = field.hasNonNull("otherRequiresApproval")
                        && field.get("otherRequiresApproval").asBoolean();
                if (!gated && field.hasNonNull("options")) {
                    for (com.fasterxml.jackson.databind.JsonNode opt : field.get("options")) {
                        if (opt.isObject() && opt.hasNonNull("requiresApproval")
                                && opt.get("requiresApproval").asBoolean()) {
                            gated = true;
                            break;
                        }
                    }
                }
                if (gated) {
                    itemsNeedingApprover.add(item.getName());
                    break;
                }
            }
        }
        out.put("itemsNeedingApprover", itemsNeedingApprover);

        List<String> policiesNoTiers = entityManager.createQuery(
                "SELECT p.name FROM SlaPolicy p WHERE p.orgId = :org AND NOT EXISTS " +
                        "(SELECT 1 FROM SlaEscalationTier t WHERE t.policy = p)", String.class)
                .setParameter("org", orgId).getResultList();
        out.put("policiesWithoutTiers", policiesNoTiers);

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

    /** Date fields an entity may be ranged on (for the dateField override). */
    private static final Map<String, Set<String>> DATE_FIELDS_BY_ENTITY = Map.of(
            "incident", Set.of("createdAt", "resolvedAt", "closedAt"),
            "issue", Set.of("createdAt"),
            "problem", Set.of("createdAt", "resolvedAt", "closedAt"),
            "change", Set.of("createdAt", "plannedStart"),
            "service_request", Set.of("createdAt", "decidedAt"));

    private Optional<Predicate> applyDateRange(CriteriaBuilder cb, Root<?> root, String entity, AdHocQueryRequest.DateRange dateRange) {
        if (dateRange == null || dateRange.from() == null || dateRange.to() == null) {
            return Optional.empty();
        }
        String dateField = dateRange.dateField() != null && !dateRange.dateField().isBlank()
                ? dateRange.dateField()
                : DATE_FIELD_BY_ENTITY.get(entity);
        if (dateField == null) {
            return Optional.empty();
        }
        if (!DATE_FIELDS_BY_ENTITY.getOrDefault(entity, Set.of()).contains(dateField)) {
            throw new IllegalArgumentException("Field not allowed for date range on " + entity + ": " + dateField);
        }
        Path<OffsetDateTime> path = root.get(dateField);
        return Optional.of(cb.between(path, dateRange.from(), dateRange.to()));
    }

    private Predicate buildPredicate(CriteriaBuilder cb, Root<?> root, AdHocQueryFilter filter, Class<?> entityClass) {
        Path<Object> path = root.get(filter.field());
        Class<?> fieldClass = path.getJavaType();

        // Blank value = "unassigned"/empty group (drill-down from a null groupKey).
        if (filter.value() == null || filter.value().isBlank()) {
            return switch (filter.op()) {
                case "eq" -> cb.isNull(path);
                case "ne" -> cb.isNotNull(path);
                default -> throw new IllegalArgumentException("Blank filter value only supports eq/ne: " + filter.op());
            };
        }

        if (BaseEntity.class.isAssignableFrom(fieldClass)) {
            return buildEntityPredicate(cb, path, filter, fieldClass);
        }

        // parseValue is lazy per-op — eager evaluation would throw
        // "No enum constant" on a comma-joined "in" list before the in-branch
        // gets a chance to split it.
        return switch (filter.op()) {
            case "eq" -> cb.equal(path, parseValue(filter.value(), fieldClass));
            case "ne" -> cb.notEqual(path, parseValue(filter.value(), fieldClass));
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
            case "eq" -> entityEquals(cb, path, filter.value(), fieldClass);
            case "ne" -> cb.not(entityEquals(cb, path, filter.value(), fieldClass));
            case "in" -> {
                List<Predicate> values = Arrays.stream(filter.value().split(","))
                        .map(String::trim)
                        .map(v -> entityEquals(cb, path, v, fieldClass))
                        .collect(Collectors.toList());
                yield cb.or(values.toArray(new Predicate[0]));
            }
            default -> throw new IllegalArgumentException("Unsupported operator: " + filter.op());
        };
    }

    /**
     * Entity-valued filters accept either the id (UUID) or the display name —
     * "Critical", "Main Clinic", a person's name — so report templates and
     * drill-downs don't need to know ids.
     */
    private Predicate entityEquals(CriteriaBuilder cb, Path<Object> path, String value, Class<?> fieldClass) {
        try {
            UUID id = UUID.fromString(value);
            return cb.equal(path, entityManager.getReference((Class<? extends BaseEntity>) fieldClass, id));
        } catch (IllegalArgumentException notUuid) {
            String nameField = AppUser.class.isAssignableFrom(fieldClass) ? "displayName" : "name";
            return cb.equal(cb.lower(path.get(nameField).as(String.class)), value.trim().toLowerCase());
        }
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

    // --- Row-level data export (Data Export page) ---------------------------

    /**
     * Admin export: full display-ready rows (names resolved, dates in
     * America/New_York), not aggregates. entity is one of incident,
     * service_request, problem, change, sla_instance.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Map<String, Object>> exportRows(UUID orgId, String entity,
                                                OffsetDateTime from, OffsetDateTime to, String status) {
        return switch (entity) {
            case "incident" -> exportIncidents(orgId, from, to, status);
            case "service_request" -> exportServiceRequests(orgId, from, to, status);
            case "problem" -> exportProblems(orgId, from, to, status);
            case "change" -> exportChanges(orgId, from, to, status);
            case "sla_instance" -> exportSlaInstances(orgId, from, to, status);
            default -> throw new IllegalArgumentException("Unknown export entity: " + entity);
        };
    }

    private void applyExportParams(TypedQuery<Tuple> q, UUID orgId,
                                   OffsetDateTime from, OffsetDateTime to, Object status) {
        q.setParameter("org", orgId);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        if (status != null) q.setParameter("status", status);
    }

    private String exportFilters(String dateField, OffsetDateTime from, OffsetDateTime to, String status) {
        StringBuilder sb = new StringBuilder();
        if (from != null) sb.append(" AND ").append(dateField).append(" >= :from");
        if (to != null) sb.append(" AND ").append(dateField).append(" <= :to");
        if (status != null && !status.isBlank()) sb.append(" AND status = :status");
        return sb.toString();
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private String fmt(OffsetDateTime t) {
        return t == null ? "" : com.alignedcardio.itsm.util.DateFormats.formatDateTime(t);
    }

    private String fmt(java.time.LocalDate d) {
        return d == null ? "" : com.alignedcardio.itsm.util.DateFormats.formatDate(d);
    }

    private List<Map<String, Object>> exportIncidents(UUID orgId, OffsetDateTime from, OffsetDateTime to, String status) {
        Incident.Status parsed = status != null && !status.isBlank()
                ? Incident.Status.valueOf(status.trim().toUpperCase()) : null;
        String jpql = """
                SELECT i.number, i.title, i.status, p.name, c.name, a.displayName, r.displayName,
                       l.name, i.createdAt, i.resolvedAt, i.closedAt
                FROM Incident i
                LEFT JOIN i.priority p LEFT JOIN i.category c LEFT JOIN i.assignee a
                LEFT JOIN i.requester r LEFT JOIN i.location l
                WHERE i.orgId = :org AND i.deletedAt IS NULL
                """ + exportFilters("i.createdAt", from, to, null)
                + (parsed != null ? " AND i.status = :status" : "")
                + " ORDER BY i.createdAt DESC";
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class);
        applyExportParams(q, orgId, from, to, parsed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            rows.add(row(
                    "number", "INC-" + t.get(0),
                    "title", t.get(1),
                    "status", t.get(2) != null ? t.get(2).toString() : "",
                    "priority", t.get(3) != null ? t.get(3) : "",
                    "category", t.get(4) != null ? t.get(4) : "",
                    "assignee", t.get(5) != null ? t.get(5) : "",
                    "requester", t.get(6) != null ? t.get(6) : "",
                    "location", t.get(7) != null ? t.get(7) : "",
                    "createdAt", fmt(t.get(8, OffsetDateTime.class)),
                    "resolvedAt", fmt(t.get(9, OffsetDateTime.class)),
                    "closedAt", fmt(t.get(10, OffsetDateTime.class))));
        }
        return rows;
    }

    private List<Map<String, Object>> exportServiceRequests(UUID orgId, OffsetDateTime from, OffsetDateTime to, String status) {
        ServiceRequest.Status parsed = status != null && !status.isBlank()
                ? ServiceRequest.Status.valueOf(status.trim().toUpperCase()) : null;
        String jpql = """
                SELECT s.number, ci.name, s.status, s.approvalDecision, r.displayName,
                       ap.displayName, l.name, pr.name, s.createdAt, s.decidedAt,
                       s.approvalBypassed, ba.displayName
                FROM ServiceRequest s
                LEFT JOIN s.catalogItem ci LEFT JOIN s.requester r LEFT JOIN s.approver ap
                LEFT JOIN s.location l LEFT JOIN s.priority pr LEFT JOIN s.bypassedBy ba
                WHERE s.orgId = :org AND s.deletedAt IS NULL
                """ + exportFilters("s.createdAt", from, to, null)
                + (parsed != null ? " AND s.status = :status" : "")
                + " ORDER BY s.createdAt DESC";
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class);
        applyExportParams(q, orgId, from, to, parsed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            boolean bypassed = Boolean.TRUE.equals(t.get(10, Boolean.class));
            rows.add(row(
                    "number", t.get(0),
                    "catalogItem", t.get(1) != null ? t.get(1) : "",
                    "status", t.get(2) != null ? t.get(2).toString() : "",
                    "approvalDecision", t.get(3) != null ? t.get(3).toString() : "",
                    "requester", t.get(4) != null ? t.get(4) : "",
                    "approver", t.get(5) != null ? t.get(5) : "",
                    "location", t.get(6) != null ? t.get(6) : "",
                    "priority", t.get(7) != null ? t.get(7) : "",
                    "createdAt", fmt(t.get(8, OffsetDateTime.class)),
                    "decidedAt", fmt(t.get(9, OffsetDateTime.class)),
                    "approvalBypassed", bypassed ? "yes" : "",
                    "bypassedBy", bypassed && t.get(11) != null ? t.get(11) : ""));
        }
        return rows;
    }

    private List<Map<String, Object>> exportProblems(UUID orgId, OffsetDateTime from, OffsetDateTime to, String status) {
        Problem.Status parsed = status != null && !status.isBlank()
                ? Problem.Status.valueOf(status.trim().toUpperCase()) : null;
        String jpql = """
                SELECT p.number, p.title, p.status, a.displayName, p.createdAt, p.resolvedAt, p.closedAt
                FROM Problem p LEFT JOIN p.assignee a
                WHERE p.orgId = :org AND p.deletedAt IS NULL
                """ + exportFilters("p.createdAt", from, to, null)
                + (parsed != null ? " AND p.status = :status" : "")
                + " ORDER BY p.createdAt DESC";
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class);
        applyExportParams(q, orgId, from, to, parsed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            rows.add(row(
                    "number", t.get(0),
                    "title", t.get(1),
                    "status", t.get(2) != null ? t.get(2).toString() : "",
                    "assignee", t.get(3) != null ? t.get(3) : "",
                    "createdAt", fmt(t.get(4, OffsetDateTime.class)),
                    "resolvedAt", fmt(t.get(5, OffsetDateTime.class)),
                    "closedAt", fmt(t.get(6, OffsetDateTime.class))));
        }
        return rows;
    }

    private List<Map<String, Object>> exportChanges(UUID orgId, OffsetDateTime from, OffsetDateTime to, String status) {
        ChangeRequest.Status parsed = status != null && !status.isBlank()
                ? ChangeRequest.Status.valueOf(status.trim().toUpperCase()) : null;
        String jpql = """
                SELECT c.number, c.title, c.status, c.changeType, c.risk,
                       a.displayName, r.displayName, c.createdAt, c.plannedStart, c.plannedEnd
                FROM ChangeRequest c LEFT JOIN c.assignee a LEFT JOIN c.requestedBy r
                WHERE c.orgId = :org AND c.deletedAt IS NULL
                """ + exportFilters("c.createdAt", from, to, null)
                + (parsed != null ? " AND c.status = :status" : "")
                + " ORDER BY c.createdAt DESC";
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class);
        applyExportParams(q, orgId, from, to, parsed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Tuple t : q.getResultList()) {
            rows.add(row(
                    "number", t.get(0),
                    "title", t.get(1),
                    "status", t.get(2) != null ? t.get(2).toString() : "",
                    "changeType", t.get(3) != null ? t.get(3).toString() : "",
                    "risk", t.get(4) != null ? t.get(4).toString() : "",
                    "assignee", t.get(5) != null ? t.get(5) : "",
                    "requestedBy", t.get(6) != null ? t.get(6) : "",
                    "createdAt", fmt(t.get(7, OffsetDateTime.class)),
                    "plannedStart", fmt(t.get(8, OffsetDateTime.class)),
                    "plannedEnd", fmt(t.get(9, OffsetDateTime.class))));
        }
        return rows;
    }

    private List<Map<String, Object>> exportSlaInstances(UUID orgId, OffsetDateTime from, OffsetDateTime to, String status) {
        SlaInstance.BreachStatus parsed = status != null && !status.isBlank()
                ? SlaInstance.BreachStatus.valueOf(status.trim().toUpperCase()) : null;
        StringBuilder jpql = new StringBuilder(
                "SELECT si FROM SlaInstance si WHERE si.orgId = :org");
        if (from != null) jpql.append(" AND si.createdAt >= :from");
        if (to != null) jpql.append(" AND si.createdAt <= :to");
        if (parsed != null) jpql.append(" AND si.breachStatus = :status");
        jpql.append(" ORDER BY si.createdAt DESC");
        TypedQuery<SlaInstance> q = entityManager.createQuery(jpql.toString(), SlaInstance.class)
                .setParameter("org", orgId);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        if (parsed != null) q.setParameter("status", parsed);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SlaInstance si : q.getResultList()) {
            String kind = si.getIncident() != null ? "INCIDENT"
                    : si.getServiceRequest() != null ? "SERVICE_REQUEST"
                    : si.getProblem() != null ? "PROBLEM"
                    : si.getChangeRequest() != null ? "CHANGE" : "";
            String ticketRef = switch (kind) {
                case "INCIDENT" -> "INC-" + si.getIncident().getNumber();
                case "SERVICE_REQUEST" -> si.getServiceRequest().getNumber();
                case "PROBLEM" -> si.getProblem().getNumber();
                case "CHANGE" -> si.getChangeRequest().getNumber();
                default -> "";
            };
            rows.add(row(
                    "entityType", kind,
                    "ticket", ticketRef,
                    "policy", si.getPolicy() != null ? si.getPolicy().getName() : "",
                    "breachStatus", si.getBreachStatus() != null ? si.getBreachStatus().name() : "",
                    "responseDueAt", fmt(si.getResponseDueAt()),
                    "responseMetAt", fmt(si.getResponseMetAt()),
                    "resolutionDueAt", fmt(si.getResolutionDueAt()),
                    "resolutionMetAt", fmt(si.getResolutionMetAt()),
                    "pausedAt", fmt(si.getPausedAt()),
                    "totalPausedMinutes", si.getTotalPausedMinutes()));
        }
        return rows;
    }

    // ---- Full Detail Export (cross-entity union) ----

    /**
     * Incidents + Service Requests in one union table with a `type`
     * discriminator, SLA status, first-response time, and last-worked-by
     * attribution. Ranged on createdAt. For the Query Builder's
     * "Full Detail Export" template — the ad-hoc engine is single-entity, so
     * this union lives here.
     */
    /**
     * Cross-entity detailed export — two row sets keyed "incidents" and
     * "serviceRequests" so callers can render separate sheets/files. Each row
     * carries full SLA + escalation detail (policy, due/met timestamps,
     * escalation level, and the tier/priority escalation audit trail).
     */
    public Map<String, List<Map<String, Object>>> fullDetailExport(UUID orgId, OffsetDateTime from, OffsetDateTime to) {
        List<Map<String, Object>> incidentRows = new ArrayList<>();
        List<Map<String, Object>> requestRows = new ArrayList<>();

        // SLA detail per entity, keyed by "TYPE:entityId".
        // 0 incidentId | 1 serviceRequestId | 2 breachStatus | 3 responseMetAt
        // 4 responseDueAt | 5 resolutionDueAt | 6 resolutionMetAt
        // 7 escalationLevel | 8 policyName
        Map<String, Tuple> slaByEntity = new HashMap<>();
        for (Tuple t : entityManager.createQuery(
                "SELECT si.incident.id, si.serviceRequest.id, si.breachStatus, si.responseMetAt, "
                        + "si.responseDueAt, si.resolutionDueAt, si.resolutionMetAt, "
                        + "si.escalationLevel, pol.name "
                        + "FROM SlaInstance si LEFT JOIN si.policy pol WHERE si.orgId = :org", Tuple.class)
                .setParameter("org", orgId).getResultList()) {
            if (t.get(0) != null) slaByEntity.put("INCIDENT:" + t.get(0), t);
            if (t.get(1) != null) slaByEntity.put("SERVICE_REQUEST:" + t.get(1), t);
        }

        List<Tuple> incidents = entityManager.createQuery(
                "SELECT i.id, i.number, i.title, i.status, p.name, c.name, l.name, "
                        + "a.displayName, r.displayName, i.createdAt, i.resolvedAt FROM Incident i "
                        + "LEFT JOIN i.priority p LEFT JOIN i.category c LEFT JOIN i.location l "
                        + "LEFT JOIN i.assignee a LEFT JOIN i.requester r "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL "
                        + "AND i.createdAt >= :from AND i.createdAt <= :to ORDER BY i.createdAt DESC",
                Tuple.class)
                .setParameter("org", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList();
        List<Tuple> requests = entityManager.createQuery(
                "SELECT s.id, s.number, ci.name, s.status, pr.name, l.name, ap.displayName, "
                        + "r.displayName, s.createdAt, s.decidedAt FROM ServiceRequest s "
                        + "LEFT JOIN s.catalogItem ci LEFT JOIN s.priority pr LEFT JOIN s.location l "
                        + "LEFT JOIN s.approver ap LEFT JOIN s.requester r "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL "
                        + "AND s.createdAt >= :from AND s.createdAt <= :to ORDER BY s.createdAt DESC",
                Tuple.class)
                .setParameter("org", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList();

        // Last worked-by: actor of the latest audit entry per selected ticket.
        Set<UUID> ticketIds = new HashSet<>();
        incidents.forEach(t -> ticketIds.add(t.get(0, UUID.class)));
        requests.forEach(t -> ticketIds.add(t.get(0, UUID.class)));
        Map<String, String> lastWorkedBy = ticketIds.isEmpty() ? Map.of() : lastWorkedByMap(orgId, ticketIds);
        Map<String, List<Tuple>> escalations = ticketIds.isEmpty() ? Map.of() : escalationHistoryMap(orgId, ticketIds);

        for (Tuple t : incidents) {
            UUID id = t.get(0, UUID.class);
            Tuple sla = slaByEntity.get("INCIDENT:" + id);
            Map<String, Object> r = row(
                    "number", "INC-" + t.get(1),
                    "title", t.get(2) != null ? t.get(2) : "",
                    "status", t.get(3) != null ? t.get(3).toString() : "",
                    "priority", t.get(4) != null ? t.get(4) : "",
                    "category", t.get(5) != null ? t.get(5) : "",
                    "location", t.get(6) != null ? t.get(6) : "",
                    "assignee", t.get(7) != null ? t.get(7) : "",
                    "requester", t.get(8) != null ? t.get(8) : "",
                    "createdAt", fmt(t.get(9, OffsetDateTime.class)),
                    "resolvedAt", fmt(t.get(10, OffsetDateTime.class)),
                    "lastWorkedBy", lastWorkedBy.getOrDefault("INCIDENT:" + id, ""));
            r.putAll(slaColumns(sla, escalations.getOrDefault("INCIDENT:" + id, List.of())));
            incidentRows.add(r);
        }
        for (Tuple t : requests) {
            UUID id = t.get(0, UUID.class);
            Tuple sla = slaByEntity.get("SERVICE_REQUEST:" + id);
            Map<String, Object> r = row(
                    "number", t.get(1),
                    "catalogItem", t.get(2) != null ? t.get(2) : "",
                    "status", t.get(3) != null ? t.get(3).toString() : "",
                    "priority", t.get(4) != null ? t.get(4) : "",
                    "location", t.get(5) != null ? t.get(5) : "",
                    "approver", t.get(6) != null ? t.get(6) : "",
                    "requester", t.get(7) != null ? t.get(7) : "",
                    "createdAt", fmt(t.get(8, OffsetDateTime.class)),
                    "decidedAt", fmt(t.get(9, OffsetDateTime.class)),
                    "lastWorkedBy", lastWorkedBy.getOrDefault("SERVICE_REQUEST:" + id, ""));
            r.putAll(slaColumns(sla, escalations.getOrDefault("SERVICE_REQUEST:" + id, List.of())));
            requestRows.add(r);
        }
        return Map.of("incidents", incidentRows, "serviceRequests", requestRows);
    }

    /** SLA + escalation columns appended to every export row. */
    private Map<String, Object> slaColumns(Tuple sla, List<Tuple> escalationEvents) {
        Map<String, Object> cols = new LinkedHashMap<>();
        OffsetDateTime resDue = sla != null ? sla.get(5, OffsetDateTime.class) : null;
        OffsetDateTime resMet = sla != null ? sla.get(6, OffsetDateTime.class) : null;
        cols.put("slaPolicy", sla != null && sla.get(8) != null ? sla.get(8) : "");
        cols.put("slaStatus", sla != null && sla.get(2) != null ? sla.get(2).toString() : "");
        cols.put("responseDueAt", sla != null ? fmt(sla.get(4, OffsetDateTime.class)) : "");
        cols.put("responseMetAt", sla != null ? fmt(sla.get(3, OffsetDateTime.class)) : "");
        cols.put("resolutionDueAt", resDue != null ? fmt(resDue) : "");
        cols.put("resolutionMetAt", resMet != null ? fmt(resMet) : "");
        cols.put("breachDurationMinutes",
                resDue != null && resMet != null && resMet.isAfter(resDue)
                        ? java.time.Duration.between(resDue, resMet).toMinutes() : "");
        cols.put("escalationLevel", sla != null ? sla.get(7) : "");
        cols.put("escalationCount", escalationEvents.size());
        cols.put("escalationHistory", escalationEvents.stream()
                .map(e -> {
                    String detail = compactDiff(e.get(3, String.class), e.get(4, String.class));
                    return fmt(e.get(5, OffsetDateTime.class)) + " " + e.get(2)
                            + (!detail.isEmpty() ? " — " + detail : "");
                })
                .collect(Collectors.joining("; ")));
        return cols;
    }

    /**
     * Escalation audit trail per entity ("TYPE:id" → ordered events).
     * One grouped query — no per-ticket round-trips.
     */
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /** "key: old → new" compact diff of two audit before/after JSON blobs. */
    private static String compactDiff(String beforeJson, String afterJson) {
        if ((beforeJson == null || beforeJson.isBlank())
                && (afterJson == null || afterJson.isBlank())) {
            return "";
        }
        try {
            Map<String, Object> before = beforeJson == null || beforeJson.isBlank()
                    ? Map.of() : JSON.readValue(beforeJson, Map.class);
            Map<String, Object> after = afterJson == null || afterJson.isBlank()
                    ? Map.of() : JSON.readValue(afterJson, Map.class);
            java.util.Set<String> keys = new java.util.TreeSet<>();
            keys.addAll(before.keySet());
            keys.addAll(after.keySet());
            List<String> parts = new ArrayList<>();
            for (String k : keys) {
                Object b = before.get(k);
                Object a = after.get(k);
                if (!java.util.Objects.equals(b, a)) {
                    parts.add(k + ": " + (b == null ? "—" : b) + " → " + (a == null ? "—" : a));
                }
            }
            return String.join(", ", parts);
        } catch (Exception e) {
            return afterJson != null ? afterJson : "";
        }
    }

    private Map<String, List<Tuple>> escalationHistoryMap(UUID orgId, Set<UUID> entityIds) {
        Map<String, List<Tuple>> byEntity = new HashMap<>();
        for (Tuple t : entityManager.createQuery(
                "SELECT a.entityType, a.entityId, a.action, a.beforeState, a.afterState, a.createdAt FROM AuditLog a "
                        + "WHERE a.orgId = :org AND a.entityId IN :ids "
                        + "AND a.action IN ('ESCALATE_PRIORITY','ESCALATE_TIER','AUTO_ESCALATE_TIER','REOPEN') "
                        + "ORDER BY a.createdAt", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", entityIds).getResultList()) {
            byEntity.computeIfAbsent(
                    t.get(0, String.class) + ":" + t.get(1, UUID.class), k -> new ArrayList<>())
                    .add(t);
        }
        return byEntity;
    }

    /** Latest-audit-actor display name per entityId ("TYPE:id" → name). */
    private Map<String, String> lastWorkedByMap(UUID orgId, Set<UUID> entityIds) {
        Map<String, UUID> actorByEntity = new LinkedHashMap<>();
        for (Tuple t : entityManager.createQuery(
                "SELECT a.entityType, a.entityId, a.actorUserId FROM AuditLog a "
                        + "WHERE a.orgId = :org AND a.entityId IN :ids "
                        + "ORDER BY a.createdAt DESC", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", entityIds).getResultList()) {
            actorByEntity.putIfAbsent(
                    t.get(0, String.class) + ":" + t.get(1, UUID.class), t.get(2, UUID.class));
        }
        Set<UUID> actorIds = actorByEntity.values().stream().filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        if (!actorIds.isEmpty()) {
            for (Tuple t : entityManager.createQuery(
                    "SELECT u.id, u.displayName FROM AppUser u WHERE u.id IN :ids", Tuple.class)
                    .setParameter("ids", actorIds).getResultList()) {
                names.put(t.get(0, UUID.class), t.get(1, String.class));
            }
        }
        Map<String, String> out = new HashMap<>();
        actorByEntity.forEach((k, actorId) -> out.put(k, names.getOrDefault(actorId, "")));
        return out;
    }

    // ---- Location Dashboard ----

    private static final List<Incident.Status> OPEN_INCIDENT_STATUSES_DASH = List.of(
            Incident.Status.NEW, Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD,
            Incident.Status.WAITING_ON_CUSTOMER, Incident.Status.REOPENED);
    private static final List<ServiceRequest.Status> OPEN_SR_STATUSES_DASH = List.of(
            ServiceRequest.Status.SUBMITTED, ServiceRequest.Status.PENDING_APPROVAL,
            ServiceRequest.Status.APPROVED, ServiceRequest.Status.IN_FULFILLMENT,
            ServiceRequest.Status.ON_HOLD, ServiceRequest.Status.REJECTED_NEEDS_REVIEW);

    /**
     * Per-location operations summary: opened in range, worked-on in range
     * (audit-driven — same definition as "Tickets I Worked On"), currently
     * pending, resolved in range. Incidents + service requests combined.
     */
    public List<Map<String, Object>> locationDashboard(UUID orgId, OffsetDateTime from, OffsetDateTime to) {
        Map<String, long[]> stats = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        entityManager.createQuery(
                        "SELECT l.name FROM Location l WHERE l.orgId = :org AND l.deletedAt IS NULL",
                        String.class)
                .setParameter("org", orgId).getResultList()
                .forEach(n -> stats.put(n, new long[4]));
        stats.putIfAbsent("(No location)", new long[4]);

        // [0] opened — createdAt in range
        addLocationCounts(stats, 0, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(i) FROM Incident i LEFT JOIN i.location l "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL "
                        + "AND i.createdAt >= :from AND i.createdAt <= :to GROUP BY l.name", from, to);
        addLocationCounts(stats, 0, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(s) FROM ServiceRequest s LEFT JOIN s.location l "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL "
                        + "AND s.createdAt >= :from AND s.createdAt <= :to GROUP BY l.name", from, to);

        // [1] worked-on — distinct tickets with audit activity in range
        List<Tuple> workedRows = entityManager.createQuery(
                "SELECT a.entityType, a.entityId FROM AuditLog a "
                        + "WHERE a.orgId = :org AND a.createdAt >= :from AND a.createdAt <= :to "
                        + "AND a.entityType IN ('INCIDENT','SERVICE_REQUEST') "
                        + "GROUP BY a.entityType, a.entityId", Tuple.class)
                .setParameter("org", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList();
        List<UUID> workedIncidents = workedRows.stream()
                .filter(t -> "INCIDENT".equals(t.get(0))).map(t -> t.get(1, UUID.class)).toList();
        List<UUID> workedRequests = workedRows.stream()
                .filter(t -> "SERVICE_REQUEST".equals(t.get(0))).map(t -> t.get(1, UUID.class)).toList();
        if (!workedIncidents.isEmpty()) {
            addLocationCountsIds(stats, 1, orgId,
                    "SELECT COALESCE(l.name, '(No location)'), COUNT(DISTINCT i.id) "
                            + "FROM Incident i LEFT JOIN i.location l "
                            + "WHERE i.orgId = :org AND i.id IN :ids GROUP BY l.name", workedIncidents);
        }
        if (!workedRequests.isEmpty()) {
            addLocationCountsIds(stats, 1, orgId,
                    "SELECT COALESCE(l.name, '(No location)'), COUNT(DISTINCT s.id) "
                            + "FROM ServiceRequest s LEFT JOIN s.location l "
                            + "WHERE s.orgId = :org AND s.id IN :ids GROUP BY l.name", workedRequests);
        }

        // [2] pending — currently open, not range-bound
        addLocationCountsOpen(stats, 2, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(i) FROM Incident i LEFT JOIN i.location l "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL AND i.status IN :statuses GROUP BY l.name",
                OPEN_INCIDENT_STATUSES_DASH);
        addLocationCountsOpen(stats, 2, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(s) FROM ServiceRequest s LEFT JOIN s.location l "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL AND s.status IN :statuses GROUP BY l.name",
                OPEN_SR_STATUSES_DASH);

        // [3] resolved — incident resolvedAt / SR fulfilled (updatedAt) in range
        addLocationCounts(stats, 3, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(i) FROM Incident i LEFT JOIN i.location l "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL "
                        + "AND i.resolvedAt >= :from AND i.resolvedAt <= :to GROUP BY l.name", from, to);
        addLocationCountsOpen(stats, 3, orgId,
                "SELECT COALESCE(l.name, '(No location)'), COUNT(s) FROM ServiceRequest s LEFT JOIN s.location l "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL AND s.status IN :statuses "
                        + "AND s.updatedAt >= :from AND s.updatedAt <= :to GROUP BY l.name",
                List.of(ServiceRequest.Status.FULFILLED), from, to);

        List<Map<String, Object>> rows = new ArrayList<>();
        stats.forEach((name, c) -> rows.add(row(
                "location", name,
                "opened", c[0],
                "workedOn", c[1],
                "pending", c[2],
                "resolved", c[3])));
        return rows;
    }

    private void addLocationCounts(Map<String, long[]> stats, int idx, UUID orgId,
                                   String jpql, OffsetDateTime from, OffsetDateTime to) {
        for (Tuple t : entityManager.createQuery(jpql, Tuple.class)
                .setParameter("org", orgId).setParameter("from", from).setParameter("to", to)
                .getResultList()) {
            stats.computeIfAbsent(t.get(0, String.class), k -> new long[4])[idx] += t.get(1, Long.class);
        }
    }

    private void addLocationCountsIds(Map<String, long[]> stats, int idx, UUID orgId,
                                      String jpql, List<UUID> ids) {
        for (Tuple t : entityManager.createQuery(jpql, Tuple.class)
                .setParameter("org", orgId).setParameter("ids", ids).getResultList()) {
            stats.computeIfAbsent(t.get(0, String.class), k -> new long[4])[idx] += t.get(1, Long.class);
        }
    }

    private void addLocationCountsOpen(Map<String, long[]> stats, int idx, UUID orgId,
                                       String jpql, List<? extends Enum<?>> statuses) {
        addLocationCountsOpen(stats, idx, orgId, jpql, statuses, null, null);
    }

    private void addLocationCountsOpen(Map<String, long[]> stats, int idx, UUID orgId,
                                       String jpql, List<? extends Enum<?>> statuses,
                                       OffsetDateTime from, OffsetDateTime to) {
        TypedQuery<Tuple> q = entityManager.createQuery(jpql, Tuple.class)
                .setParameter("org", orgId).setParameter("statuses", statuses);
        if (from != null) q.setParameter("from", from);
        if (to != null) q.setParameter("to", to);
        for (Tuple t : q.getResultList()) {
            stats.computeIfAbsent(t.get(0, String.class), k -> new long[4])[idx] += t.get(1, Long.class);
        }
    }

    // ---- Agent Workload Detail (SUPER_ADMIN, grouped by tier) ----

    /**
     * Per-tier workload: every member of L1/L2/L3/IT Fulfillment, including
     * members with zero work (the gap the monthly Agent Performance table has).
     * Metrics: open assigned, audit actions in range, resolved in range,
     * currently-overdue assigned, SLA compliance %.
     */
    public List<Map<String, Object>> agentWorkloadDetail(UUID orgId, OffsetDateTime from, OffsetDateTime to) {
        List<UUID> tierIds = List.of(SupportTiers.L1_ID, SupportTiers.L2_ID,
                SupportTiers.L3_ID, SupportTiers.IT_FULFILLMENT_ID);
        Map<UUID, String> tierNames = new HashMap<>();
        for (Team t : entityManager.createQuery(
                "SELECT t FROM Team t WHERE t.id IN :ids", Team.class)
                .setParameter("ids", tierIds).getResultList()) {
            tierNames.put(t.getId(), t.getName());
        }
        Map<UUID, String> tierFallback = Map.of(
                SupportTiers.L1_ID, "L1 Support", SupportTiers.L2_ID, "L2 Support",
                SupportTiers.L3_ID, "L3 Support", SupportTiers.IT_FULFILLMENT_ID, "IT Fulfillment");

        // team.id -> [member rows]
        Map<UUID, List<Map<String, Object>>> members = new LinkedHashMap<>();
        tierIds.forEach(id -> members.put(id, new ArrayList<>()));
        for (Tuple t : entityManager.createQuery(
                "SELECT tm.team.id, u.id, u.displayName FROM TeamMember tm JOIN tm.user u "
                        + "WHERE tm.team.id IN :ids AND u.deletedAt IS NULL "
                        + "ORDER BY tm.team.id, u.displayName", Tuple.class)
                .setParameter("ids", tierIds).getResultList()) {
            UUID teamId = t.get(0, UUID.class);
            members.computeIfAbsent(teamId, k -> new ArrayList<>()).add(row(
                    "agentId", t.get(1, UUID.class).toString(),
                    "name", t.get(2, String.class),
                    "openAssigned", 0L, "worked", 0L, "resolved", 0L,
                    "overdue", 0L, "slaPercent", null));
        }

        Set<UUID> agentIds = members.values().stream().flatMap(List::stream)
                .map(m -> UUID.fromString((String) m.get("agentId"))).collect(Collectors.toSet());
        if (agentIds.isEmpty()) {
            return tierIds.stream().map(id -> Map.<String, Object>of(
                    "team", tierNames.getOrDefault(id, tierFallback.get(id)),
                    "teamId", id.toString(),
                    "members", List.<Map<String, Object>>of())).collect(Collectors.toList());
        }

        Map<UUID, long[]> m = new HashMap<>(); // [open, worked, resolved, overdue]
        agentIds.forEach(id -> m.put(id, new long[4]));
        Map<UUID, long[]> sla = new HashMap<>(); // [total, breached]

        // open assigned: open incidents + open fulfillment tasks
        for (Tuple t : entityManager.createQuery(
                "SELECT a.id, COUNT(i) FROM Incident i JOIN i.assignee a "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL AND i.status IN :statuses "
                        + "AND a.id IN :ids GROUP BY a.id", Tuple.class)
                .setParameter("org", orgId).setParameter("statuses", OPEN_INCIDENT_STATUSES_DASH)
                .setParameter("ids", agentIds).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[0] += t.get(1, Long.class);
        }
        for (Tuple t : entityManager.createQuery(
                "SELECT ft.assignee.id, COUNT(ft) FROM FulfillmentTask ft JOIN ft.serviceRequest s "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL AND ft.assignee.id IN :ids "
                        + "AND ft.status IN :statuses GROUP BY ft.assignee.id", Tuple.class)
                .setParameter("org", orgId).setParameter("statuses", WORKLOAD_TASK_STATUSES)
                .setParameter("ids", agentIds).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[0] += t.get(1, Long.class);
        }

        // worked: audit actions in range
        for (Tuple t : entityManager.createQuery(
                "SELECT a.actorUserId, COUNT(a) FROM AuditLog a "
                        + "WHERE a.orgId = :org AND a.actorUserId IN :ids "
                        + "AND a.createdAt >= :from AND a.createdAt <= :to "
                        + "GROUP BY a.actorUserId", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", agentIds)
                .setParameter("from", from).setParameter("to", to).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[1] += t.get(1, Long.class);
        }

        // resolved: incidents resolvedAt + tasks deliveredAt in range.
        // Legacy imports carry resolvedAt + a single import assignee — exclude
        // them so one agent doesn't appear to have resolved ~1000 tickets.
        for (Tuple t : entityManager.createQuery(
                "SELECT a.id, COUNT(i) FROM Incident i JOIN i.assignee a "
                        + "WHERE i.orgId = :org AND i.deletedAt IS NULL AND a.id IN :ids "
                        + "AND i.legacyImport = false "
                        + "AND i.resolvedAt >= :from AND i.resolvedAt <= :to GROUP BY a.id", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", agentIds)
                .setParameter("from", from).setParameter("to", to).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[2] += t.get(1, Long.class);
        }
        for (Tuple t : entityManager.createQuery(
                "SELECT ft.assignee.id, COUNT(ft) FROM FulfillmentTask ft JOIN ft.serviceRequest s "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL AND ft.assignee.id IN :ids "
                        + "AND ft.deliveredAt >= :from AND ft.deliveredAt <= :to "
                        + "GROUP BY ft.assignee.id", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", agentIds)
                .setParameter("from", from).setParameter("to", to).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[2] += t.get(1, Long.class);
        }

        // overdue assigned: breached SLA on open incidents + overdue tasks
        for (Tuple t : entityManager.createQuery(
                "SELECT a.id, COUNT(i) FROM SlaInstance si JOIN si.incident i JOIN i.assignee a "
                        + "WHERE si.orgId = :org AND si.breachStatus = :breached "
                        + "AND i.deletedAt IS NULL AND i.status IN :statuses AND a.id IN :ids "
                        + "GROUP BY a.id", Tuple.class)
                .setParameter("org", orgId).setParameter("statuses", OPEN_INCIDENT_STATUSES_DASH)
                .setParameter("breached", SlaInstance.BreachStatus.BREACHED)
                .setParameter("ids", agentIds).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[3] += t.get(1, Long.class);
        }
        for (Tuple t : entityManager.createQuery(
                "SELECT ft.assignee.id, COUNT(ft) FROM FulfillmentTask ft JOIN ft.serviceRequest s "
                        + "WHERE s.orgId = :org AND s.deletedAt IS NULL AND ft.assignee.id IN :ids "
                        + "AND ft.expectedDeliveryDate < :today AND ft.deliveredAt IS NULL "
                        + "AND ft.status <> :completed GROUP BY ft.assignee.id", Tuple.class)
                .setParameter("org", orgId).setParameter("ids", agentIds)
                .setParameter("completed", FulfillmentTask.Status.COMPLETED)
                .setParameter("today", LocalDate.now()).getResultList()) {
            m.computeIfAbsent(t.get(0, UUID.class), k -> new long[4])[3] += t.get(1, Long.class);
        }

        // SLA compliance: instances on incidents assigned to the agent
        for (Tuple t : entityManager.createQuery(
                "SELECT a.id, COUNT(si), "
                        + "SUM(CASE WHEN si.breachStatus = :breached THEN 1 ELSE 0 END) "
                        + "FROM SlaInstance si JOIN si.incident i JOIN i.assignee a "
                        + "WHERE si.orgId = :org AND i.deletedAt IS NULL AND a.id IN :ids "
                        + "GROUP BY a.id", Tuple.class)
                .setParameter("breached", SlaInstance.BreachStatus.BREACHED)
                .setParameter("org", orgId).setParameter("ids", agentIds).getResultList()) {
            sla.put(t.get(0, UUID.class), new long[]{t.get(1, Long.class), t.get(2, Long.class)});
        }

        List<Map<String, Object>> out = new ArrayList<>();
        for (UUID tierId : tierIds) {
            List<Map<String, Object>> memberRows = members.getOrDefault(tierId, List.of()).stream()
                    .map(member -> {
                        UUID agentId = UUID.fromString((String) member.get("agentId"));
                        long[] c = m.getOrDefault(agentId, new long[4]);
                        long[] s = sla.get(agentId);
                        Object slaPercent = s == null || s[0] == 0 ? null
                                : Math.round((s[0] - s[1]) * 1000.0 / s[0]) / 10.0;
                        Map<String, Object> mr = new LinkedHashMap<>(member);
                        mr.put("openAssigned", c[0]);
                        mr.put("worked", c[1]);
                        mr.put("resolved", c[2]);
                        mr.put("overdue", c[3]);
                        mr.put("slaPercent", slaPercent);
                        return mr;
                    }).collect(Collectors.toList());
            out.add(Map.of(
                    "team", tierNames.getOrDefault(tierId, tierFallback.get(tierId)),
                    "teamId", tierId.toString(),
                    "members", memberRows));
        }
        return out;
    }

    // ---- SLA trend by priority (monthly) ----

    /** Monthly SLA compliance split by ticket priority (incidents + requests). */
    public List<Map<String, Object>> slaComplianceMonthlyByPriority(UUID orgId, int months) {
        if (months < 1 || months > 60) {
            throw new IllegalArgumentException("months must be between 1 and 60");
        }
        OffsetDateTime start = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMonths(months).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
        OffsetDateTime now = OffsetDateTime.now();

        List<Tuple> rows = entityManager.createQuery(
                "SELECT si.createdAt, si.resolutionDueAt, si.resolutionMetAt, ip.name, sp.name "
                        + "FROM SlaInstance si "
                        + "LEFT JOIN si.incident i LEFT JOIN i.priority ip "
                        + "LEFT JOIN si.serviceRequest s LEFT JOIN s.priority sp "
                        + "WHERE si.orgId = :org AND si.createdAt >= :start", Tuple.class)
                .setParameter("org", orgId).setParameter("start", start).getResultList();

        record MonthKey(YearMonth month, String priority) implements Comparable<MonthKey> {
            @Override public int compareTo(MonthKey o) {
                int c = month.compareTo(o.month);
                return c != 0 ? c : priority.compareTo(o.priority);
            }
        }
        record Sla(long total, long breached) {}

        Map<MonthKey, Sla> byMonthPriority = new TreeMap<>();
        for (Tuple t : rows) {
            OffsetDateTime createdAt = t.get(0, OffsetDateTime.class);
            if (createdAt == null) continue;
            String priority = t.get(3, String.class) != null ? t.get(3, String.class)
                    : t.get(4, String.class) != null ? t.get(4, String.class) : "None";
            MonthKey key = new MonthKey(YearMonth.from(createdAt), priority);
            Sla cur = byMonthPriority.getOrDefault(key, new Sla(0, 0));
            boolean breached = isBreachedAtDue(t.get(1, OffsetDateTime.class),
                    t.get(2, OffsetDateTime.class), now);
            byMonthPriority.put(key, new Sla(cur.total() + 1, cur.breached() + (breached ? 1 : 0)));
        }

        List<Map<String, Object>> result = new ArrayList<>();
        byMonthPriority.forEach((k, s) -> result.add(Map.of(
                "month", k.month().toString(),
                "priority", k.priority(),
                "total", s.total(),
                "breached", s.breached(),
                "compliancePercent", s.total() == 0 ? 100.0
                        : Math.round((s.total() - s.breached()) * 10000.0 / s.total()) / 100.0)));
        return result;
    }
}
