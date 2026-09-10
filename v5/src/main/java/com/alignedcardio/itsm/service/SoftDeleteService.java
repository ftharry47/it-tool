package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.BaseEntity;
import com.alignedcardio.itsm.repository.ChangeRequestRepository;
import com.alignedcardio.itsm.repository.IncidentRepository;
import com.alignedcardio.itsm.repository.ProblemRepository;
import com.alignedcardio.itsm.repository.ServiceRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class SoftDeleteService {

    private final IncidentRepository incidentRepository;
    private final ServiceRequestRepository serviceRequestRepository;
    private final ProblemRepository problemRepository;
    private final ChangeRequestRepository changeRequestRepository;

    public SoftDeleteService(IncidentRepository incidentRepository,
                             ServiceRequestRepository serviceRequestRepository,
                             ProblemRepository problemRepository,
                             ChangeRequestRepository changeRequestRepository) {
        this.incidentRepository = incidentRepository;
        this.serviceRequestRepository = serviceRequestRepository;
        this.problemRepository = problemRepository;
        this.changeRequestRepository = changeRequestRepository;
    }

    @Transactional
    public void softDelete(AppUser user, UUID orgId, String entityType, List<UUID> ids) {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID id : ids) {
            findByOrgIdAndId(orgId, entityType, id).ifPresent(entity -> {
                if (entity.getDeletedAt() != null) {
                    return;
                }
                entity.setDeletedAt(now);
                entity.setUpdatedAt(now);
                entity.setUpdatedBy(user.getId());
                save(entityType, entity);
            });
        }
    }

    @Transactional
    public void restore(AppUser user, UUID orgId, String entityType, List<UUID> ids) {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID id : ids) {
            findByOrgIdAndId(orgId, entityType, id).ifPresent(entity -> {
                if (entity.getDeletedAt() == null) {
                    return;
                }
                entity.setDeletedAt(null);
                entity.setUpdatedAt(now);
                entity.setUpdatedBy(user.getId());
                save(entityType, entity);
            });
        }
    }

    private Optional<? extends BaseEntity> findByOrgIdAndId(UUID orgId, String entityType, UUID id) {
        return switch (entityType) {
            case "incidents" -> incidentRepository.findByOrgIdAndId(orgId, id);
            case "service-requests" -> serviceRequestRepository.findByOrgIdAndId(orgId, id);
            case "problems" -> problemRepository.findByOrgIdAndId(orgId, id);
            case "change-requests" -> changeRequestRepository.findByOrgIdAndId(orgId, id);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    private void save(String entityType, BaseEntity entity) {
        switch (entityType) {
            case "incidents" -> incidentRepository.save((com.alignedcardio.itsm.entity.Incident) entity);
            case "service-requests" -> serviceRequestRepository.save((com.alignedcardio.itsm.entity.ServiceRequest) entity);
            case "problems" -> problemRepository.save((com.alignedcardio.itsm.entity.Problem) entity);
            case "change-requests" -> changeRequestRepository.save((com.alignedcardio.itsm.entity.ChangeRequest) entity);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }
}
