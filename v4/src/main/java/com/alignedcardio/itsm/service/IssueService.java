package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.*;
import com.alignedcardio.itsm.entity.*;
import com.alignedcardio.itsm.event.IssueCreatedEvent;
import com.alignedcardio.itsm.event.IssueStatusChangedEvent;
import com.alignedcardio.itsm.repository.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IssueService {

    private final IssueRepository issueRepository;
    private final IssueTypeRepository issueTypeRepository;
    private final ProjectRepository projectRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionValidator workflowTransitionValidator;
    private final AppUserRepository appUserRepository;
    private final TeamRepository teamRepository;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;

    public IssueService(IssueRepository issueRepository,
                        IssueTypeRepository issueTypeRepository,
                        ProjectRepository projectRepository,
                        WorkflowRepository workflowRepository,
                        WorkflowStatusRepository workflowStatusRepository,
                        WorkflowTransitionValidator workflowTransitionValidator,
                        AppUserRepository appUserRepository,
                        TeamRepository teamRepository,
                        EntityManager entityManager,
                        ApplicationEventPublisher eventPublisher) {
        this.issueRepository = issueRepository;
        this.issueTypeRepository = issueTypeRepository;
        this.projectRepository = projectRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionValidator = workflowTransitionValidator;
        this.appUserRepository = appUserRepository;
        this.teamRepository = teamRepository;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public IssueResponse create(UUID orgId, UUID createdBy, IssueCreateRequest request) {
        Project project = projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        Workflow workflow = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.workflowId())
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        WorkflowStatus status = workflowStatusRepository.findByWorkflowIdAndId(request.workflowId(), request.workflowStatusId())
                .orElseThrow(() -> new NotFoundException("Workflow status not found"));

        Issue issue = new Issue();
        issue.setOrgId(orgId);
        issue.setProject(project);
        issue.setWorkflow(workflow);
        issue.setWorkflowStatus(status);
        issue.setIssueType(request.issueTypeId() != null ? issueTypeRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.issueTypeId()).orElse(null) : null);
        if (request.sprintId() != null) {
            issue.setSprint(entityManager.getReference(Sprint.class, request.sprintId()));
        }
        issue.setSummary(request.summary());
        issue.setDescription(request.description());
        issue.setAssignee(request.assigneeId() != null ? appUserRepository.findById(request.assigneeId()).orElse(null) : null);
        issue.setReporter(appUserRepository.findById(createdBy).orElseThrow(() -> new NotFoundException("Reporter not found")));
        issue.setStoryPoints(request.storyPoints());
        issue.setRemainingPoints(request.storyPoints() != null ? request.storyPoints() : 0);
        issue.setPriority(request.priority() != null ? request.priority() : Issue.Priority.MEDIUM);
        if (request.epicId() != null) {
            Issue epic = issueRepository.findById(request.epicId())
                    .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                    .orElse(null);
            issue.setEpic(epic);
        }
        if (request.parentIssueId() != null) {
            Issue parent = issueRepository.findById(request.parentIssueId())
                    .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                    .orElse(null);
            issue.setParentIssue(parent);
        }
        issue.setCreatedBy(createdBy);
        issue.setUpdatedBy(createdBy);

        int number = nextNumber(project.getId());
        issue.setNumber(number);
        issue.setKey(project.getKey() + "-" + number);

        Issue saved = issueRepository.save(issue);

        eventPublisher.publishEvent(new IssueCreatedEvent(
                saved.getOrgId(),
                saved.getId(),
                Map.of(
                        "id", saved.getId(),
                        "key", saved.getKey(),
                        "projectId", saved.getProject().getId(),
                        "workflowStatusId", saved.getWorkflowStatus().getId(),
                        "priority", saved.getPriority().name())));

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> listByProject(UUID orgId, UUID projectId) {
        return issueRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> backlog(UUID orgId, UUID projectId) {
        return issueRepository.findByProjectIdAndSprintIdIsNullAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> listBySprint(UUID orgId, UUID sprintId) {
        return issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprintId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IssueResponse get(UUID orgId, UUID issueId) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        return toResponse(issue);
    }

    @Transactional(readOnly = true)
    public List<BoardColumn> board(UUID orgId, UUID sprintId) {
        List<Issue> issues = issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(sprintId);
        List<WorkflowStatus> statuses = workflowStatusRepository.findByWorkflowIdOrderByDisplayOrderAsc(issues.isEmpty() ? null : issues.get(0).getWorkflow().getId());

        Map<UUID, List<Issue>> byStatus = issues.stream().collect(Collectors.groupingBy(i -> i.getWorkflowStatus().getId(), LinkedHashMap::new, Collectors.toList()));
        List<BoardColumn> columns = new ArrayList<>();
        for (WorkflowStatus s : statuses) {
            List<IssueResponse> items = byStatus.getOrDefault(s.getId(), List.of()).stream().map(this::toResponse).toList();
            columns.add(new BoardColumn(s.getName(), s.getCategory().name(), s.getDisplayOrder(), items));
        }
        return columns;
    }

    @Transactional(readOnly = true)
    public List<BoardColumn> boardByProject(UUID orgId, UUID projectId, UUID workflowId, UUID sprintId) {
        List<Issue> issues = issueRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId)
                .stream()
                .filter(i -> i.getWorkflow().getId().equals(workflowId))
                .filter(i -> sprintId == null || (i.getSprint() != null && i.getSprint().getId().equals(sprintId)))
                .toList();
        List<WorkflowStatus> statuses = workflowStatusRepository.findByWorkflowIdOrderByDisplayOrderAsc(workflowId);

        Map<UUID, List<Issue>> byStatus = issues.stream().collect(Collectors.groupingBy(i -> i.getWorkflowStatus().getId(), LinkedHashMap::new, Collectors.toList()));
        List<BoardColumn> columns = new ArrayList<>();
        for (WorkflowStatus s : statuses) {
            List<IssueResponse> items = byStatus.getOrDefault(s.getId(), List.of()).stream().map(this::toResponse).toList();
            columns.add(new BoardColumn(s.getName(), s.getCategory().name(), s.getDisplayOrder(), items));
        }
        return columns;
    }

    @Transactional
    public IssueResponse update(UUID orgId, UUID updatedBy, UUID issueId, IssueCreateRequest request) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        if (request.summary() != null) issue.setSummary(request.summary());
        if (request.description() != null) issue.setDescription(request.description());
        if (request.issueTypeId() != null) issue.setIssueType(issueTypeRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.issueTypeId()).orElse(null));
        if (request.assigneeId() != null) issue.setAssignee(appUserRepository.findById(request.assigneeId()).orElse(null));
        if (request.sprintId() != null) {
            issue.setSprint(entityManager.getReference(Sprint.class, request.sprintId()));
        }
        if (request.storyPoints() != null) {
            issue.setStoryPoints(request.storyPoints());
            if (issue.getRemainingPoints() == null || request.storyPoints() > issue.getRemainingPoints()) {
                issue.setRemainingPoints(request.storyPoints());
            }
        }
        if (request.priority() != null) issue.setPriority(request.priority());
        if (request.epicId() != null) {
            Issue epic = issueRepository.findById(request.epicId())
                    .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                    .orElse(null);
            issue.setEpic(epic);
        }
        if (request.parentIssueId() != null) {
            Issue parent = issueRepository.findById(request.parentIssueId())
                    .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                    .orElse(null);
            issue.setParentIssue(parent);
        }
        issue.setUpdatedBy(updatedBy);
        issue.setUpdatedAt(OffsetDateTime.now());
        return toResponse(issueRepository.save(issue));
    }

    @Transactional
    public void updateField(UUID orgId, UUID updatedBy, UUID issueId, String field, String value) {
        if ("status".equalsIgnoreCase(field) || "workflowstatus".equalsIgnoreCase(field)) {
            throw new IllegalStateException("SET_FIELD does not support status; use SET_STATUS to enforce the workflow transition validator");
        }

        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        switch (field.toLowerCase()) {
            case "assigneeid" -> {
                if (value == null || value.isBlank()) {
                    issue.setAssignee(null);
                } else {
                    issue.setAssignee(appUserRepository.findById(UUID.fromString(value)).orElse(null));
                }
            }
            case "priority" -> issue.setPriority(Issue.Priority.valueOf(value.toUpperCase()));
            default -> throw new IllegalStateException("Unsupported SET_FIELD target: " + field);
        }

        issue.setUpdatedBy(updatedBy);
        issue.setUpdatedAt(OffsetDateTime.now());
        issueRepository.save(issue);
    }

    @Transactional
    public IssueResponse assign(UUID orgId, UUID updatedBy, UUID issueId, UUID assigneeId) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        if (assigneeId == null) {
            issue.setAssignee(null);
        } else {
            AppUser assignee = appUserRepository.findById(assigneeId)
                    .orElseThrow(() -> new NotFoundException("Assignee not found"));
            issue.setAssignee(assignee);
        }

        issue.setUpdatedBy(updatedBy);
        issue.setUpdatedAt(OffsetDateTime.now());
        return toResponse(issueRepository.save(issue));
    }

    @Transactional
    public IssueResponse assignTeam(UUID orgId, UUID updatedBy, UUID issueId, UUID teamId) {
        Issue issue = issueRepository.findById(issueId)
                .filter(i -> i.getDeletedAt() == null && i.getOrgId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Issue not found"));

        if (teamId == null) {
            issue.setAssignmentTeam(null);
        } else {
            Team team = teamRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, teamId)
                    .orElseThrow(() -> new NotFoundException("Team not found"));
            issue.setAssignmentTeam(team);
        }

        issue.setUpdatedBy(updatedBy);
        issue.setUpdatedAt(OffsetDateTime.now());
        return toResponse(issueRepository.save(issue));
    }

    @Transactional
    public IssueResponse changeStatus(UUID orgId, UUID updatedBy, UUID issueId, IssueStatusChangeRequest request) {
        Issue issue = issueRepository.findByProjectIdAndIdAndDeletedAtIsNull(orgId, issueId)
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        WorkflowStatus newStatus = workflowStatusRepository.findByWorkflowIdAndId(issue.getWorkflow().getId(), request.workflowStatusId())
                .orElseThrow(() -> new NotFoundException("Target workflow status not found"));

        workflowTransitionValidator.validate(issue.getWorkflow().getId(), issue.getWorkflowStatus().getId(), newStatus.getId());

        UUID oldStatusId = issue.getWorkflowStatus().getId();
        issue.setWorkflowStatus(newStatus);
        issue.setUpdatedBy(updatedBy);
        issue.setUpdatedAt(OffsetDateTime.now());

        Issue saved = issueRepository.save(issue);

        eventPublisher.publishEvent(new IssueStatusChangedEvent(
                saved.getOrgId(),
                saved.getId(),
                Map.of(
                        "id", saved.getId(),
                        "key", saved.getKey(),
                        "projectId", saved.getProject().getId(),
                        "oldWorkflowStatusId", oldStatusId,
                        "newWorkflowStatusId", saved.getWorkflowStatus().getId())));

        return toResponse(saved);
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID issueId) {
        Issue issue = issueRepository.findByProjectIdAndIdAndDeletedAtIsNull(orgId, issueId)
                .orElseThrow(() -> new NotFoundException("Issue not found"));
        issue.setDeletedAt(OffsetDateTime.now());
        issue.setUpdatedBy(deletedBy);
        issueRepository.save(issue);
    }

    private String sequenceName(UUID projectId) {
        return "issue_number_" + projectId.toString().replace("-", "_");
    }

    private int nextNumber(UUID projectId) {
        String seqName = sequenceName(projectId);
        entityManager.createNativeQuery("CREATE SEQUENCE IF NOT EXISTS " + seqName)
                .executeUpdate();
        Number n = (Number) entityManager.createNativeQuery(
                "SELECT nextval('" + seqName + "')")
                .getSingleResult();
        return n.intValue();
    }

    private IssueResponse toResponse(Issue i) {
        return new IssueResponse(
                i.getId(),
                i.getProject().getId(),
                i.getKey(),
                i.getIssueType() != null ? i.getIssueType().getId() : null,
                i.getIssueType() != null ? i.getIssueType().getName() : null,
                i.getWorkflow().getId(),
                i.getWorkflowStatus().getId(),
                i.getWorkflowStatus().getName(),
                i.getWorkflowStatus().getCategory(),
                i.getSprint() != null ? i.getSprint().getId() : null,
                i.getEpic() != null ? i.getEpic().getId() : null,
                i.getParentIssue() != null ? i.getParentIssue().getId() : null,
                i.getSummary(),
                i.getDescription(),
                i.getAssignee() != null ? i.getAssignee().getId() : null,
                i.getAssignee() != null ? i.getAssignee().getDisplayName() : null,
                i.getReporter().getId(),
                i.getReporter().getDisplayName(),
                i.getStoryPoints(),
                i.getRemainingPoints(),
                i.getPriority()
        );
    }
}
