package com.alignedcardio.itsm.service.reporting;

import com.alignedcardio.itsm.entity.SavedReport;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.repository.SavedReportRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SavedReportService {

    private final SavedReportRepository repository;
    private final ObjectMapper objectMapper;

    public SavedReportService(SavedReportRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<SavedReport> list(UUID orgId) {
        return repository.findByOrgIdOrderByCreatedAtDesc(orgId);
    }

    /**
     * Reports visible to a specific user: org-wide ad-hoc reports plus any
     * generated reports (e.g. AGENT_PERFORMANCE) owned by that user.
     */
    @Transactional(readOnly = true)
    public List<SavedReport> listForUser(UUID orgId, UUID userId) {
        return repository.findByOrgIdAndOwnerUserIdIsNullOrOwnerUserIdOrderByCreatedAtDesc(orgId, userId);
    }

    /** All generated reports of a type — SUPER_ADMIN aggregate view. */
    @Transactional(readOnly = true)
    public List<SavedReport> listByType(UUID orgId, String reportType) {
        return repository.findByOrgIdAndReportTypeOrderByCreatedAtDesc(orgId, reportType);
    }

    @Transactional(readOnly = true)
    public Optional<SavedReport> get(UUID id, UUID orgId) {
        return repository.findByIdAndOrgId(id, orgId);
    }

    @Transactional
    public SavedReport create(AppUser user, String name, String entity,
                              List<AdHocQueryFilter> filters, String groupBy,
                              AdHocQueryRequest.DateRange dateRange) {
        SavedReport report = new SavedReport();
        report.setOrgId(user.getOrgId());
        report.setName(name);
        report.setEntity(entity);
        report.setGroupBy(groupBy);
        report.setCreatedBy(user.getId());
        report.setUpdatedBy(user.getId());
        try {
            report.setFilters(objectMapper.writeValueAsString(filters));
            report.setDateRange(objectMapper.writeValueAsString(dateRange));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid report definition", e);
        }
        return repository.save(report);
    }

    @Transactional
    public Optional<SavedReport> update(UUID id, UUID orgId, Map<String, Object> changes) {
        Optional<SavedReport> existing = repository.findByIdAndOrgId(id, orgId);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        SavedReport report = existing.get();
        if (changes.containsKey("name")) {
            report.setName((String) changes.get("name"));
        }
        if (changes.containsKey("entity")) {
            report.setEntity((String) changes.get("entity"));
        }
        if (changes.containsKey("groupBy")) {
            report.setGroupBy((String) changes.get("groupBy"));
        }
        if (changes.containsKey("filters")) {
            try {
                report.setFilters(objectMapper.writeValueAsString(changes.get("filters")));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid filters", e);
            }
        }
        if (changes.containsKey("dateRange")) {
            try {
                report.setDateRange(objectMapper.writeValueAsString(changes.get("dateRange")));
            } catch (JsonProcessingException e) {
                throw new IllegalArgumentException("Invalid dateRange", e);
            }
        }
        return Optional.of(repository.save(report));
    }

    @Transactional
    public boolean delete(UUID id, UUID orgId) {
        Optional<SavedReport> existing = repository.findByIdAndOrgId(id, orgId);
        if (existing.isEmpty()) {
            return false;
        }
        existing.get().softDelete();
        repository.save(existing.get());
        return true;
    }
}
