package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.catalog.CatalogItemCreateRequest;
import com.alignedcardio.itsm.api.catalog.CatalogItemResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.CatalogItem;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.CatalogItemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public ServiceCatalogService(CatalogItemRepository catalogItemRepository,
                                 AppUserRepository appUserRepository,
                                 ObjectMapper objectMapper,
                                 EntityManager entityManager) {
        this.catalogItemRepository = catalogItemRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> list(UUID orgId) {
        return catalogItemRepository.findByOrgIdOrderByNameAsc(orgId).stream()
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
        try {
            item.setFormSchema(objectMapper.readTree(request.formSchema()));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid form schema JSON", e);
        }
        item.setApprovalRequired(request.approvalRequired());
        item.setActive(request.active());
        item.setCreatedBy(user.getId());
        item.setUpdatedBy(user.getId());

        if (request.approverId() != null) {
            AppUser approver = appUserRepository.findById(request.approverId())
                    .orElseThrow(() -> new NotFoundException("Approver not found"));
            item.setApprover(approver);
        }

        item.setFulfillmentTasks(parseJson(request.fulfillmentTasks()));

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
        try {
            item.setFormSchema(objectMapper.readTree(request.formSchema()));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid form schema JSON", e);
        }
        item.setApprovalRequired(request.approvalRequired());
        item.setActive(request.active());

        if (request.approverId() != null) {
            AppUser approver = appUserRepository.findById(request.approverId())
                    .orElseThrow(() -> new NotFoundException("Approver not found"));
            item.setApprover(approver);
        } else {
            item.setApprover(null);
        }

        item.setFulfillmentTasks(parseJson(request.fulfillmentTasks()));
        item.setUpdatedBy(user.getId());
        item.setUpdatedAt(OffsetDateTime.now());

        return toResponse(catalogItemRepository.save(item));
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid JSON: " + json, e);
        }
    }

    private CatalogItemResponse toResponse(CatalogItem item) {
        return new CatalogItemResponse(
                item.getId(),
                item.getName(),
                item.getDescription(),
                item.getCategory(),
                item.getFormSchema().toString(),
                item.isApprovalRequired(),
                item.getApprover() != null ? item.getApprover().getId() : null,
                item.getApprover() != null ? item.getApprover().getDisplayName() : null,
                item.getFulfillmentTasks() != null ? item.getFulfillmentTasks().toString() : "[]",
                item.isActive(),
                item.getCreatedAt()
        );
    }
}
