package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.ProjectCreateRequest;
import com.alignedcardio.itsm.api.project.ProjectResponse;
import com.alignedcardio.itsm.api.project.ProjectUpdateRequest;
import com.alignedcardio.itsm.entity.Project;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.ProjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final AppUserRepository appUserRepository;

    public ProjectService(ProjectRepository projectRepository, AppUserRepository appUserRepository) {
        this.projectRepository = projectRepository;
        this.appUserRepository = appUserRepository;
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
        return toResponse(projectRepository.save(p));
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

    private ProjectResponse toResponse(Project p) {
        UUID leadId = p.getLead() != null ? p.getLead().getId() : null;
        String leadName = p.getLead() != null ? p.getLead().getDisplayName() : null;
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
