package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.project.IssueTypeCreateRequest;
import com.alignedcardio.itsm.api.project.IssueTypeResponse;
import com.alignedcardio.itsm.entity.IssueType;
import com.alignedcardio.itsm.repository.IssueTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class IssueTypeService {

    private final IssueTypeRepository issueTypeRepository;

    public IssueTypeService(IssueTypeRepository issueTypeRepository) {
        this.issueTypeRepository = issueTypeRepository;
    }

    @Transactional
    public IssueTypeResponse create(UUID orgId, UUID createdBy, IssueTypeCreateRequest request) {
        IssueType it = new IssueType();
        it.setOrgId(orgId);
        it.setName(request.name());
        it.setDescription(request.description());
        it.setIcon(request.icon());
        it.setColor(request.color());
        it.setCreatedBy(createdBy);
        it.setUpdatedBy(createdBy);
        return toResponse(issueTypeRepository.save(it));
    }

    @Transactional(readOnly = true)
    public List<IssueTypeResponse> list(UUID orgId) {
        return issueTypeRepository.findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(orgId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public IssueTypeResponse get(UUID orgId, UUID id) {
        IssueType it = issueTypeRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Issue type not found"));
        return toResponse(it);
    }

    @Transactional
    public IssueTypeResponse update(UUID orgId, UUID updatedBy, UUID id, IssueTypeCreateRequest request) {
        IssueType it = issueTypeRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Issue type not found"));
        if (request.name() != null) it.setName(request.name());
        if (request.description() != null) it.setDescription(request.description());
        if (request.icon() != null) it.setIcon(request.icon());
        if (request.color() != null) it.setColor(request.color());
        it.setUpdatedBy(updatedBy);
        it.setUpdatedAt(OffsetDateTime.now());
        return toResponse(issueTypeRepository.save(it));
    }

    @Transactional
    public void delete(UUID orgId, UUID deletedBy, UUID id) {
        IssueType it = issueTypeRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Issue type not found"));
        it.setDeletedAt(OffsetDateTime.now());
        it.setUpdatedBy(deletedBy);
        issueTypeRepository.save(it);
    }

    private IssueTypeResponse toResponse(IssueType it) {
        return new IssueTypeResponse(
                it.getId(),
                it.getName(),
                it.getDescription(),
                it.getIcon(),
                it.getColor()
        );
    }
}
