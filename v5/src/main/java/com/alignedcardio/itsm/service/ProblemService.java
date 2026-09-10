package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.problem.*;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.entity.Problem;
import com.alignedcardio.itsm.entity.ProblemIncidentLink;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.ProblemIncidentLinkRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import com.alignedcardio.itsm.service.notification.NotificationRequest;
import com.alignedcardio.itsm.service.notification.NotificationService;
import com.alignedcardio.itsm.service.notification.NotificationTemplateBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProblemService {

    private static final Logger logger = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final ProblemIncidentLinkRepository problemIncidentLinkRepository;
    private final IncidentRepository incidentRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final NotificationTemplateBuilder notificationTemplateBuilder;

    public ProblemService(ProblemRepository problemRepository,
                          ProblemIncidentLinkRepository problemIncidentLinkRepository,
                          IncidentRepository incidentRepository,
                          AppUserRepository appUserRepository,
                          EntityManager entityManager,
                          AuditLogRepository auditLogRepository,
                          ObjectMapper objectMapper,
                          AuditLogService auditLogService,
                          NotificationService notificationService,
                          NotificationTemplateBuilder notificationTemplateBuilder) {
        this.problemRepository = problemRepository;
        this.problemIncidentLinkRepository = problemIncidentLinkRepository;
        this.incidentRepository = incidentRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
        this.notificationService = notificationService;
        this.notificationTemplateBuilder = notificationTemplateBuilder;
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list(UUID orgId) {
        return list(orgId, null, false);
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> list(UUID orgId, UUID assigneeId, boolean showDeleted) {
        List<Problem> problems = assigneeId == null
                ? problemRepository.findByOrgIdOrderByCreatedAtDesc(orgId)
                : problemRepository.findByOrgIdAndAssigneeIdOrderByCreatedAtDesc(orgId, assigneeId);
        return problems.stream()
                .filter(p -> showDeleted ? p.getDeletedAt() != null : p.getDeletedAt() == null)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProblemResponse> search(UUID orgId, String query, int limit) {
        return problemRepository.searchByText(orgId, query, PageRequest.of(0, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ProblemResponse create(AppUser user, UUID orgId, ProblemCreateRequest request) {
        if (!isProblemContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can create problems");
        }

        Problem problem = new Problem();
        problem.setOrgId(orgId);
        problem.setNumber(generateProblemNumber());
        problem.setTitle(request.title());
        problem.setDescription(request.description());
        problem.setCreatedBy(user.getId());
        problem.setUpdatedBy(user.getId());

        if (request.assigneeId() != null) {
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            problem.setAssignee(assignee);
        }

        Problem saved = problemRepository.save(problem);
        entityManager.flush();
        entityManager.refresh(saved);

        if (saved.getAssignee() != null) {
            publishAssignment(saved, user, saved.getAssignee());
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID orgId, UUID id) {
        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        return toResponse(problem);
    }

    @Transactional
    public ProblemResponse update(AppUser user, UUID orgId, UUID id, ProblemUpdateRequest request) {
        if (!isProblemContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can update problems");
        }

        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        Map<String, Object> beforeState = problemAuditState(problem);
        Problem.Status oldStatus = problem.getStatus();

        if (request.title() != null) problem.setTitle(request.title());
        if (request.description() != null) problem.setDescription(request.description());
        if (request.rootCause() != null) problem.setRootCause(request.rootCause());
        if (request.workaround() != null) problem.setWorkaround(request.workaround());

        UUID previousAssigneeId = problem.getAssignee() == null ? null : problem.getAssignee().getId();
        if (request.assigneeId() != null) {
            AppUser assignee = appUserRepository.findById(request.assigneeId())
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            problem.setAssignee(assignee);
        }

        if (request.status() != null && request.status() != problem.getStatus()) {
            ProblemStatusMachine.validate(problem.getStatus(), request.status());
            validateProblemTransition(user, problem.getStatus(), request.status());
            setStatusTimestamps(problem, request.status());
            problem.setStatus(request.status());
        }

        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        Problem saved = problemRepository.save(problem);
        if (saved.getStatus() != oldStatus) {
            writeProblemAudit(saved, user.getId(), "STATUS",
                    Map.of("status", oldStatus.name()),
                    Map.of("status", saved.getStatus().name()));
        }
        writeProblemFieldUpdateAudit(saved, user.getId(), beforeState, saved.getStatus() != oldStatus);

        if (saved.getAssignee() != null && !Objects.equals(previousAssigneeId, saved.getAssignee().getId())) {
            publishAssignment(saved, user, saved.getAssignee());
        }

        return toResponse(saved);
    }

    @Transactional
    public ProblemResponse updateStatus(AppUser user, UUID orgId, UUID id, Problem.Status newStatus) {
        if (!isProblemContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can update problem status");
        }

        Problem problem = problemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        Problem.Status oldStatus = problem.getStatus();

        ProblemStatusMachine.validate(problem.getStatus(), newStatus);
        validateProblemTransition(user, problem.getStatus(), newStatus);
        setStatusTimestamps(problem, newStatus);
        problem.setStatus(newStatus);
        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        Problem saved = problemRepository.save(problem);
        writeProblemAudit(saved, user.getId(), "STATUS",
                Map.of("status", oldStatus.name()),
                Map.of("status", saved.getStatus().name()));

        return toResponse(saved);
    }

    @Transactional
    public ProblemResponse linkIncident(AppUser user, UUID orgId, UUID problemId, LinkIncidentRequest request) {
        if (!isProblemContributor(user)) {
            throw new IllegalStateException("Only AGENT, TEAM_LEAD, ADMIN, or SUPER_ADMIN can link incidents to problems");
        }

        Problem problem = problemRepository.findByOrgIdAndId(orgId, problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        Incident incident = incidentRepository.findByOrgIdAndId(orgId, request.incidentId())
                .orElseThrow(() -> new NotFoundException("Incident not found"));

        if (problemIncidentLinkRepository.existsByProblemIdAndIncidentId(problem.getId(), incident.getId())) {
            throw new IllegalStateException("Incident already linked to this problem");
        }

        ProblemIncidentLink link = new ProblemIncidentLink();
        link.setProblemId(problem.getId());
        link.setIncidentId(incident.getId());
        problemIncidentLinkRepository.save(link);

        problem.setUpdatedBy(user.getId());
        problem.setUpdatedAt(OffsetDateTime.now());

        Problem saved = problemRepository.save(problem);
        writeProblemAudit(saved, user.getId(), "LINK_INCIDENT", null,
                Map.of("incidentId", incident.getId(), "incidentNumber", String.valueOf(incident.getNumber())));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IncidentSummary> listLinkedIncidents(UUID orgId, UUID problemId) {
        return problemIncidentLinkRepository.findByProblemId(problemId).stream()
                .map(ProblemIncidentLink::getIncidentId)
                .map(incidentRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toIncidentSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.alignedcardio.itsm.api.auth.AuditLogResponse> listActivity(UUID orgId, UUID problemId) {
        problemRepository.findByOrgIdAndId(orgId, problemId)
                .orElseThrow(() -> new NotFoundException("Problem not found"));
        return auditLogRepository
                .findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(orgId, "PROBLEM", problemId)
                .stream()
                .map(auditLogService::toResponse)
                .toList();
    }

    private Map<String, Object> problemAuditState(Problem problem) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("title", problem.getTitle());
        state.put("description", problem.getDescription());
        state.put("rootCause", problem.getRootCause());
        state.put("workaround", problem.getWorkaround());
        state.put("status", problem.getStatus() == null ? null : problem.getStatus().name());
        state.put("assigneeId", problem.getAssignee() == null ? null : problem.getAssignee().getId());
        state.put("assigneeName", problem.getAssignee() == null ? null : problem.getAssignee().getDisplayName());
        return state;
    }

    private void writeProblemFieldUpdateAudit(Problem problem, UUID actorId, Map<String, Object> beforeState, boolean excludeStatus) {
        Map<String, Object> afterState = problemAuditState(problem);
        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        for (String key : afterState.keySet()) {
            if (excludeStatus && "status".equals(key)) {
                continue;
            }
            if (!java.util.Objects.equals(beforeState.get(key), afterState.get(key))) {
                before.put(key, beforeState.get(key));
                after.put(key, afterState.get(key));
            }
        }
        if (!after.isEmpty()) {
            writeProblemAudit(problem, actorId, "UPDATE", before, after);
        }
    }

    private void writeProblemAudit(Problem problem, UUID actorId, String action,
                                   Map<String, Object> beforeState, Map<String, Object> afterState) {
        try {
            AuditLog log = new AuditLog();
            log.setOrgId(problem.getOrgId());
            log.setActorUserId(actorId);
            log.setAction(action);
            log.setEntityType("PROBLEM");
            log.setEntityId(problem.getId());
            log.setBeforeState(beforeState == null ? null : objectMapper.writeValueAsString(beforeState));
            log.setAfterState(afterState == null ? null : objectMapper.writeValueAsString(afterState));
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to write problem audit log for {}", problem.getId(), e);
        }
    }

    private void validateProblemTransition(AppUser user, Problem.Status from, Problem.Status to) {
        if (to == Problem.Status.CLOSED && !isProblemAdmin(user)) {
            throw new IllegalStateException("Only ADMIN or SUPER_ADMIN can close a problem");
        }
    }

    private boolean isProblemAdmin(AppUser user) {
        return hasAnyRole(user, "ADMIN", "SUPER_ADMIN", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean isProblemContributor(AppUser user) {
        return hasAnyRole(user, "AGENT", "TEAM_LEAD", "ADMIN", "SUPER_ADMIN",
                "ROLE_AGENT", "ROLE_TEAM_LEAD", "ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    private boolean hasAnyRole(AppUser user, String... names) {
        if (user == null || user.getUserRoles() == null) {
            return false;
        }
        List<String> targets = List.of(names);
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> ur.getRole().getName())
                .anyMatch(targets::contains);
    }

    private void setStatusTimestamps(Problem problem, Problem.Status newStatus) {
        if (newStatus == Problem.Status.RESOLVED) {
            problem.setResolvedAt(OffsetDateTime.now());
        } else if (newStatus == Problem.Status.CLOSED) {
            problem.setClosedAt(OffsetDateTime.now());
        } else if (newStatus == Problem.Status.INVESTIGATING || newStatus == Problem.Status.KNOWN_ERROR) {
            problem.setResolvedAt(null);
            problem.setClosedAt(null);
        }
    }

    private ProblemResponse toResponse(Problem problem) {
        UUID assigneeId = problem.getAssignee() == null ? null : problem.getAssignee().getId();
        AppUser assignee = assigneeId == null ? null : appUserRepository.findById(assigneeId).orElse(null);
        return new ProblemResponse(
                problem.getId(),
                problem.getNumber(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getStatus(),
                problem.getRootCause(),
                problem.getWorkaround(),
                assignee == null ? null : assignee.getId(),
                assignee == null ? null : assignee.getDisplayName(),
                problem.getResolvedAt(),
                problem.getClosedAt(),
                problem.getCreatedAt()
        );
    }

    private void publishAssignment(Problem saved, AppUser updater, AppUser assignee) {
        if (assignee.getId().equals(updater.getId())) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("number", saved.getNumber());
            payload.put("title", saved.getTitle());
            payload.put("actorName", updater.getDisplayName());
            payload.put("assigneeName", assignee.getDisplayName());
            payload.put("entityType", "PROBLEM");
            payload.put("entityId", saved.getId());
            var content = notificationTemplateBuilder.forEvent("PROBLEM_ASSIGNED", payload);
            notificationService.send(new NotificationRequest(
                    saved.getOrgId(),
                    assignee.getId(),
                    "PROBLEM_ASSIGNED",
                    content.inAppSubject(),
                    content.inAppBody(),
                    "PROBLEM",
                    saved.getId(),
                    null,
                    content));
        } catch (Exception e) {
            logger.warn("Failed to send problem assignment notification to {}", assignee.getId(), e);
        }
    }

    private String generateProblemNumber() {
        Long next = ((Number) entityManager.createNativeQuery("SELECT nextval('problem_number_seq')")
                .getSingleResult()).longValue();
        return "PROB-" + next;
    }

    private IncidentSummary toIncidentSummary(Incident incident) {
        return new IncidentSummary(
                incident.getId(),
                incident.getNumber(),
                incident.getTitle(),
                incident.getStatus().name(),
                incident.getPriority() != null ? incident.getPriority().getName() : null,
                incident.getCategory() != null ? incident.getCategory().getName() : null,
                incident.getRequester() != null ? incident.getRequester().getDisplayName() : null,
                incident.getAssignee() != null ? incident.getAssignee().getDisplayName() : null,
                incident.getCreatedAt()
        );
    }

    public record IncidentSummary(
            UUID id,
            Long number,
            String title,
            String status,
            String priority,
            String category,
            String requester,
            String assignee,
            OffsetDateTime createdAt
    ) {
    }
}
