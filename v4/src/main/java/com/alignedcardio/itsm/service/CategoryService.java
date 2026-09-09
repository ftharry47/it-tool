package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.category.CategoryRequest;
import com.alignedcardio.itsm.api.category.CategoryResponse;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.Category;
import com.alignedcardio.itsm.repository.CategoryRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final IncidentRepository incidentRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           IncidentRepository incidentRepository) {
        this.categoryRepository = categoryRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID orgId) {
        return categoryRepository.findByOrgIdAndDeletedAtIsNullOrderByDisplayOrderAsc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse create(AppUser user, UUID orgId, CategoryRequest request) {
        if (categoryRepository.findByOrgIdAndNameIgnoreCaseAndDeletedAtIsNull(orgId, request.name().trim()).isPresent()) {
            throw new IllegalStateException("A category with this name already exists");
        }
        Category category = new Category();
        category.setOrgId(orgId);
        apply(category, request);
        category.setCreatedBy(user.getId());
        category.setUpdatedBy(user.getId());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(AppUser user, UUID orgId, UUID id, CategoryRequest request) {
        Category category = categoryRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        categoryRepository.findByOrgIdAndNameIgnoreCaseAndDeletedAtIsNull(orgId, request.name().trim())
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalStateException("A category with this name already exists");
                });
        apply(category, request);
        category.setUpdatedBy(user.getId());
        category.setUpdatedAt(OffsetDateTime.now());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID orgId, UUID id) {
        Category category = categoryRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
        if (incidentRepository.existsByCategory_Id(id)) {
            throw new IllegalStateException("Category is in use by incidents and cannot be deleted");
        }
        category.softDelete();
        categoryRepository.save(category);
    }

    private void apply(Category category, CategoryRequest request) {
        category.setName(request.name().trim());
        category.setDescription(request.description());
        if (request.displayOrder() != null) {
            category.setDisplayOrder(request.displayOrder());
        }
        if (request.status() != null && !request.status().isBlank()) {
            category.setStatus(Category.Status.valueOf(request.status()));
        }
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(
                c.getId(),
                c.getName(),
                c.getDescription(),
                c.getDisplayOrder(),
                c.getStatus().name(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
