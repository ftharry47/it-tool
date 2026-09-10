package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.SprintCompleteRequest;
import com.alignedcardio.itsm.api.project.SprintCreateRequest;
import com.alignedcardio.itsm.api.project.SprintResponse;
import com.alignedcardio.itsm.entity.Issue;
import com.alignedcardio.itsm.entity.Project;
import com.alignedcardio.itsm.entity.Sprint;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.repository.IssueRepository;
import com.alignedcardio.itsm.repository.ProjectRepository;
import com.alignedcardio.itsm.repository.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SprintService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final IssueRepository issueRepository;

    public SprintService(SprintRepository sprintRepository,
                         ProjectRepository projectRepository,
                         IssueRepository issueRepository) {
        this.sprintRepository = sprintRepository;
        this.projectRepository = projectRepository;
        this.issueRepository = issueRepository;
    }

    @Transactional
    public SprintResponse create(UUID orgId, UUID createdBy, SprintCreateRequest request) {
        Project project = projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.projectId())
                .orElseThrow(() -> new NotFoundException("Project not found"));
        Sprint s = new Sprint();
        s.setOrgId(orgId);
        s.setProject(project);
        s.setName(request.name());
        s.setGoal(request.goal());
        s.setStartDate(request.startDate());
        s.setEndDate(request.endDate());
        s.setStatus(Sprint.Status.PLANNING);
        s.setCreatedBy(createdBy);
        s.setUpdatedBy(createdBy);
        return toResponse(sprintRepository.save(s));
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> listByProject(UUID orgId, UUID projectId) {
        return sprintRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public SprintResponse get(UUID orgId, UUID projectId, UUID id) {
        Sprint s = sprintRepository.findByProjectIdAndId(projectId, id)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        return toResponse(s);
    }

    @Transactional
    public SprintResponse update(UUID orgId, UUID updatedBy, UUID id, SprintCreateRequest request) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        if (!s.getProject().getId().equals(request.projectId())) {
            throw new IllegalStateException("Cannot move sprint to a different project");
        }
        if (request.name() != null) s.setName(request.name());
        if (request.goal() != null) s.setGoal(request.goal());
        if (request.startDate() != null) s.setStartDate(request.startDate());
        if (request.endDate() != null) s.setEndDate(request.endDate());
        s.setUpdatedBy(updatedBy);
        s.setUpdatedAt(OffsetDateTime.now());
        return toResponse(sprintRepository.save(s));
    }

    @Transactional
    public SprintResponse start(UUID orgId, UUID updatedBy, UUID id) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        if (s.getStatus() != Sprint.Status.PLANNING) {
            throw new IllegalStateException("Only planning sprints can be started");
        }
        s.setStatus(Sprint.Status.ACTIVE);
        s.setStartDate(OffsetDateTime.now());
        s.setUpdatedBy(updatedBy);
        s.setUpdatedAt(OffsetDateTime.now());
        return toResponse(sprintRepository.save(s));
    }

    @Transactional
    public SprintResponse complete(UUID orgId, UUID updatedBy, UUID id, SprintCompleteRequest request) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        if (s.getStatus() != Sprint.Status.ACTIVE) {
            throw new IllegalStateException("Only active sprints can be completed");
        }
        if (request.destination() == null) {
            throw new IllegalStateException("A destination is required to complete a sprint");
        }
        if (request.destination() == SprintCompleteRequest.Destination.NEXT_SPRINT && request.nextSprintId() == null) {
            throw new IllegalStateException("nextSprintId is required when destination is NEXT_SPRINT");
        }

        Sprint nextSprint = request.nextSprintId() != null
                ? sprintRepository.findById(request.nextSprintId()).orElseThrow(() -> new NotFoundException("Next sprint not found"))
                : null;

        List<Issue> issues = issueRepository.findBySprintIdAndDeletedAtIsNullOrderByWorkflowStatusIdAscUpdatedAtDesc(id);
        for (Issue issue : issues) {
            if (isDone(issue)) {
                continue;
            }
            if (request.destination() == SprintCompleteRequest.Destination.BACKLOG) {
                issue.setSprint(null);
            } else {
                issue.setSprint(nextSprint);
            }
            issue.setUpdatedBy(updatedBy);
            issue.setUpdatedAt(OffsetDateTime.now());
        }

        s.setStatus(Sprint.Status.COMPLETED);
        s.setCompletedAt(OffsetDateTime.now());
        s.setUpdatedBy(updatedBy);
        s.setUpdatedAt(OffsetDateTime.now());
        return toResponse(sprintRepository.save(s));
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID id) {
        Sprint s = sprintRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Sprint not found"));
        if (s.getStatus() == Sprint.Status.ACTIVE) {
            throw new IllegalStateException("Cannot delete an active sprint");
        }
        s.setDeletedAt(OffsetDateTime.now());
        s.setUpdatedBy(deletedBy);
        s.setUpdatedAt(OffsetDateTime.now());
        sprintRepository.save(s);
    }

    private boolean isDone(Issue issue) {
        return issue.getWorkflowStatus() != null && issue.getWorkflowStatus().getCategory() == WorkflowStatus.Category.DONE;
    }

    private SprintResponse toResponse(Sprint s) {
        return new SprintResponse(
                s.getId(),
                s.getProject().getId(),
                s.getName(),
                s.getGoal(),
                s.getStatus().name(),
                s.getStartDate(),
                s.getEndDate(),
                s.getCompletedAt()
        );
    }
}
