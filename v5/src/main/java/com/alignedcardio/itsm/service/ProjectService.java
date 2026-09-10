package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.incident.NotFoundException;
import com.alignedcardio.itsm.api.project.ProjectCreateRequest;
import com.alignedcardio.itsm.api.project.ProjectResponse;
import com.alignedcardio.itsm.api.project.ProjectUpdateRequest;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Project;
import com.alignedcardio.itsm.entity.Workflow;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.entity.WorkflowTransition;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.ProjectRepository;
import com.alignedcardio.itsm.repository.WorkflowRepository;
import com.alignedcardio.itsm.repository.WorkflowStatusRepository;
import com.alignedcardio.itsm.repository.WorkflowTransitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;
    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;

    public ProjectService(ProjectRepository projectRepository,
                          AppUserRepository appUserRepository,
                          WorkflowRepository workflowRepository,
                          WorkflowStatusRepository workflowStatusRepository,
                          WorkflowTransitionRepository workflowTransitionRepository) {
        this.projectRepository = projectRepository;
        this.appUserRepository = appUserRepository;
        this.workflowRepository = workflowRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
    }

    @Transactional
    public ProjectResponse create(UUID orgId, UUID createdBy, ProjectCreateRequest request) {
        Project p = new Project();
        p.setOrgId(orgId);
        p.setKey(request.key().toUpperCase());
        p.setName(request.name());
        p.setDescription(request.description());
        p.setStatus(Project.Status.ACTIVE);
        if (request.leadId() != null) {
            p.setLead(appUserRepository.findById(request.leadId()).orElse(null));
        }
        p.setCreatedBy(createdBy);
        p.setUpdatedBy(createdBy);
        Project saved = projectRepository.save(p);
        createDefaultWorkflow(saved, orgId, createdBy);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID orgId) {
        return projectRepository.findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(orgId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID orgId, UUID id) {
        Project p = projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        return toResponse(p);
    }

    @Transactional
    public ProjectResponse update(UUID orgId, UUID updatedBy, UUID id, ProjectUpdateRequest request) {
        Project p = projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        if (request.name() != null) p.setName(request.name());
        if (request.description() != null) p.setDescription(request.description());
        if (request.leadId() != null) {
            p.setLead(appUserRepository.findById(request.leadId()).orElse(null));
        }
        if (request.status() != null) {
            p.setStatus(Project.Status.valueOf(request.status().toUpperCase()));
        }
        p.setUpdatedBy(updatedBy);
        p.setUpdatedAt(OffsetDateTime.now());
        return toResponse(projectRepository.save(p));
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID id) {
        Project p = projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Project not found"));
        p.setDeletedAt(OffsetDateTime.now());
        p.setUpdatedBy(deletedBy);
        projectRepository.save(p);
    }

    private void createDefaultWorkflow(Project project, UUID orgId, UUID createdBy) {
        Workflow workflow = new Workflow();
        workflow.setOrgId(orgId);
        workflow.setProject(project);
        workflow.setName(project.getName() + " Workflow");
        workflow.setDescription("Default kanban workflow for " + project.getName());
        workflow.setCreatedBy(createdBy);
        workflow.setUpdatedBy(createdBy);
        Workflow savedWorkflow = workflowRepository.save(workflow);

        List<WorkflowStatus> statuses = new ArrayList<>();
        statuses.add(createStatus(savedWorkflow, orgId, "Backlog", WorkflowStatus.Category.BACKLOG, 0, false, createdBy));
        statuses.add(createStatus(savedWorkflow, orgId, "To Do", WorkflowStatus.Category.TODO, 1, false, createdBy));
        statuses.add(createStatus(savedWorkflow, orgId, "In Progress", WorkflowStatus.Category.IN_PROGRESS, 2, false, createdBy));
        statuses.add(createStatus(savedWorkflow, orgId, "Done", WorkflowStatus.Category.DONE, 3, true, createdBy));

        for (WorkflowStatus from : statuses) {
            for (WorkflowStatus to : statuses) {
                if (!from.getId().equals(to.getId())) {
                    createTransition(savedWorkflow, orgId, from, to, createdBy);
                }
            }
        }
    }

    private WorkflowStatus createStatus(Workflow workflow, UUID orgId, String name, WorkflowStatus.Category category, int displayOrder, boolean terminal, UUID createdBy) {
        WorkflowStatus s = new WorkflowStatus();
        s.setWorkflow(workflow);
        s.setOrgId(orgId);
        s.setName(name);
        s.setCategory(category);
        s.setDisplayOrder(displayOrder);
        s.setTerminal(terminal);
        s.setCreatedBy(createdBy);
        s.setUpdatedBy(createdBy);
        return workflowStatusRepository.save(s);
    }

    private void createTransition(Workflow workflow, UUID orgId, WorkflowStatus from, WorkflowStatus to, UUID createdBy) {
        WorkflowTransition t = new WorkflowTransition();
        t.setWorkflow(workflow);
        t.setOrgId(orgId);
        t.setFromStatus(from);
        t.setToStatus(to);
        t.setCreatedBy(createdBy);
        t.setUpdatedBy(createdBy);
        workflowTransitionRepository.save(t);
    }

    private ProjectResponse toResponse(Project p) {
        UUID leadId = null;
        String leadName = null;
        if (p.getLead() != null) {
            AppUser lead = appUserRepository.findById(p.getLead().getId()).orElse(null);
            if (lead != null) {
                leadId = lead.getId();
                leadName = lead.getDisplayName();
            }
        }
        return new ProjectResponse(
                p.getId(),
                p.getKey(),
                p.getName(),
                p.getDescription(),
                leadId,
                leadName,
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
