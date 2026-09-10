package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.*;
import com.alignedcardio.itsm.entity.Workflow;
import com.alignedcardio.itsm.entity.WorkflowStatus;
import com.alignedcardio.itsm.entity.WorkflowTransition;
import com.alignedcardio.itsm.repository.ProjectRepository;
import com.alignedcardio.itsm.repository.WorkflowRepository;
import com.alignedcardio.itsm.repository.WorkflowStatusRepository;
import com.alignedcardio.itsm.repository.WorkflowTransitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final WorkflowStatusRepository workflowStatusRepository;
    private final WorkflowTransitionRepository workflowTransitionRepository;
    private final ProjectRepository projectRepository;

    public WorkflowService(WorkflowRepository workflowRepository,
                           WorkflowStatusRepository workflowStatusRepository,
                           WorkflowTransitionRepository workflowTransitionRepository,
                           ProjectRepository projectRepository) {
        this.workflowRepository = workflowRepository;
        this.workflowStatusRepository = workflowStatusRepository;
        this.workflowTransitionRepository = workflowTransitionRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public WorkflowResponse create(UUID orgId, UUID createdBy, WorkflowCreateRequest request) {
        Workflow w = new Workflow();
        w.setOrgId(orgId);
        w.setName(request.name());
        w.setDescription(request.description());
        if (request.projectId() != null) {
            w.setProject(projectRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, request.projectId()).orElse(null));
        }
        w.setCreatedBy(createdBy);
        w.setUpdatedBy(createdBy);
        Workflow saved = workflowRepository.save(w);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<WorkflowResponse> list(UUID orgId) {
        return workflowRepository.findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(orgId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkflowResponse get(UUID orgId, UUID id) {
        Workflow w = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        return toResponse(w);
    }

    @Transactional
    public WorkflowResponse update(UUID orgId, UUID updatedBy, UUID id, WorkflowCreateRequest request) {
        Workflow w = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        if (request.name() != null) w.setName(request.name());
        if (request.description() != null) w.setDescription(request.description());
        w.setUpdatedBy(updatedBy);
        w.setUpdatedAt(OffsetDateTime.now());
        return toResponse(workflowRepository.save(w));
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID id) {
        Workflow w = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        w.setDeletedAt(OffsetDateTime.now());
        w.setUpdatedBy(deletedBy);
        workflowRepository.save(w);
    }

    @Transactional
    public WorkflowStatusResponse addStatus(UUID orgId, UUID updatedBy, UUID workflowId, WorkflowStatusCreateRequest request) {
        Workflow w = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        WorkflowStatus s = new WorkflowStatus();
        s.setWorkflow(w);
        s.setOrgId(orgId);
        s.setName(request.name());
        s.setCategory(request.category());
        s.setDisplayOrder(request.displayOrder());
        s.setTerminal(request.terminal());
        s.setCreatedBy(updatedBy);
        s.setUpdatedBy(updatedBy);
        return toStatusResponse(workflowStatusRepository.save(s));
    }

    @Transactional
    public void removeStatus(UUID orgId, UUID workflowId, UUID statusId) {
        WorkflowStatus s = workflowStatusRepository.findByWorkflowIdAndId(workflowId, statusId)
                .orElseThrow(() -> new NotFoundException("Workflow status not found"));
        workflowStatusRepository.delete(s);
    }

    @Transactional
    public WorkflowTransitionResponse addTransition(UUID orgId, UUID updatedBy, UUID workflowId, WorkflowTransitionCreateRequest request) {
        Workflow w = workflowRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow not found"));
        WorkflowStatus from = workflowStatusRepository.findByWorkflowIdAndId(workflowId, request.fromStatusId())
                .orElseThrow(() -> new NotFoundException("From status not found"));
        WorkflowStatus to = workflowStatusRepository.findByWorkflowIdAndId(workflowId, request.toStatusId())
                .orElseThrow(() -> new NotFoundException("To status not found"));

        WorkflowTransition t = new WorkflowTransition();
        t.setWorkflow(w);
        t.setOrgId(orgId);
        t.setFromStatus(from);
        t.setToStatus(to);
        t.setScreen(request.screen());
        t.setCreatedBy(updatedBy);
        t.setUpdatedBy(updatedBy);
        return toTransitionResponse(workflowTransitionRepository.save(t));
    }

    @Transactional
    public void removeTransition(UUID orgId, UUID workflowId, UUID transitionId) {
        WorkflowTransition t = workflowTransitionRepository.findById(transitionId)
                .filter(tr -> tr.getWorkflow().getId().equals(workflowId))
                .orElseThrow(() -> new NotFoundException("Workflow transition not found"));
        workflowTransitionRepository.delete(t);
    }

    private WorkflowResponse toResponse(Workflow w) {
        UUID projectId = w.getProject() != null ? w.getProject().getId() : null;
        List<WorkflowStatus> statuses = workflowStatusRepository.findByWorkflowIdOrderByDisplayOrderAsc(w.getId());
        List<WorkflowTransition> transitions = workflowTransitionRepository.findByWorkflowId(w.getId());
        return new WorkflowResponse(
                w.getId(),
                w.getName(),
                w.getDescription(),
                projectId,
                statuses.stream().map(this::toStatusResponse).toList(),
                transitions.stream().map(this::toTransitionResponse).toList()
        );
    }

    private WorkflowStatusResponse toStatusResponse(WorkflowStatus s) {
        return new WorkflowStatusResponse(
                s.getId(),
                s.getName(),
                s.getCategory(),
                s.getDisplayOrder(),
                s.isTerminal()
        );
    }

    private WorkflowTransitionResponse toTransitionResponse(WorkflowTransition t) {
        return new WorkflowTransitionResponse(
                t.getId(),
                t.getFromStatus().getId(),
                t.getFromStatus().getName(),
                t.getToStatus().getId(),
                t.getToStatus().getName(),
                t.getScreen()
        );
    }
}
