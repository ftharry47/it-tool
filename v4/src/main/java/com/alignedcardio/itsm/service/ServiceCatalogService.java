package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.catalog.CatalogItemCreateRequest;
import com.alignedcardio.itsm.api.catalog.CatalogItemResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.CatalogItemRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ServiceCatalogService {

    private final CatalogItemRepository catalogItemRepository;
    private final AppUserRepository appUserRepository;
    private final EntityManager entityManager;

    public ServiceCatalogService(CatalogItemRepository catalogItemRepository,
                                 AppUserRepository appUserRepository,
                                 EntityManager entityManager) {
        this.catalogItemRepository = catalogItemRepository;
        this.appUserRepository = appUserRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> list(UUID orgId) {
        return catalogItemRepository.findByOrgIdAndActiveTrueOrderByNameAsc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CatalogItemResponse create(AppUser user, UUID orgId, CatalogItemCreateRequest request) {
        CatalogItem item = new CatalogItem();
        item.setOrgId(orgId);
        item.setName(request.name());
        item.setDescription(request.description());
        item.setCategory(request.category());
        item.setFormSchema(request.formSchema());
        item.setApprovalRequired(request.approvalRequired());
        item.setActive(request.active());
        item.setCreatedBy(user.getId());
        item.setUpdatedBy(user.getId());

        if (request.approverId() != null) {
            AppUser approver = appUserRepository.findById(request.approverId())
                    .orElseThrow(() -> new NotFoundException("Approver not found"));
            item.setApprover(approver);
        }

        item.setFulfillmentTasks(request.fulfillmentTasks());

        CatalogItem saved = catalogItemRepository.save(item);
        entityManager.flush();
        entityManager.refresh(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse get(UUID orgId, UUID id) {
        CatalogItem item = catalogItemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));
        return toResponse(item);
    }

    @Transactional
    public CatalogItemResponse update(AppUser user, UUID orgId, UUID id, CatalogItemCreateRequest request) {
        CatalogItem item = catalogItemRepository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException("Catalog item not found"));

        item.setName(request.name());
        item.setDescription(request.description());
        item.setCategory(request.category());
        item.setFormSchema(request.formSchema());
        item.setApprovalRequired(request.approvalRequired());
        item.setActive(request.active());

        if (request.approverId() != null) {
            AppUser approver = appUserRepository.findById(request.approverId())
                    .orElseThrow(() -> new NotFoundException("Approver not found"));
            item.setApprover(approver);
        } else {
            item.setApprover(null);
        }

        item.setFulfillmentTasks(request.fulfillmentTasks());
        item.setUpdatedBy(user.getId());
        item.setUpdatedAt(OffsetDateTime.now());

        return toResponse(catalogItemRepository.save(item));
    }

    private CatalogItemResponse toResponse(CatalogItem item) {
        return new CatalogItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getFormSchema(),
                item.isApprovalRequired(),
                item.getApprover() != null ? item.getApprover().getId() : null,
                item.getApprover() != null ? item.getApprover().getDisplayName() : null,
                item.getFulfillmentTasks(),
                item.isActive(),
                item.getCreatedAt()
        );
    }
}
