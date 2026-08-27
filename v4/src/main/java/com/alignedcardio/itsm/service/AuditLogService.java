package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.auth.AuditLogResponse;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final int PAGE_SIZE = 25;

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> list(UUID orgId, String entityType, OffsetDateTime from, OffsetDateTime to, int page) {
        Pageable pageable = PageRequest.of(page, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<AuditLog> result;
        if (entityType != null && !entityType.isBlank() && from != null && to != null) {
            result = auditLogRepository.findByOrgIdAndEntityTypeAndCreatedAtBetween(orgId, entityType, from, to, pageable);
        } else if (entityType != null && !entityType.isBlank() && from != null) {
            result = auditLogRepository.findByOrgIdAndEntityTypeAndCreatedAtGreaterThanEqual(orgId, entityType, from, pageable);
        } else if (entityType != null && !entityType.isBlank() && to != null) {
            result = auditLogRepository.findByOrgIdAndEntityTypeAndCreatedAtLessThanEqual(orgId, entityType, to, pageable);
        } else if (entityType != null && !entityType.isBlank()) {
            result = auditLogRepository.findByOrgIdAndEntityType(orgId, entityType, pageable);
        } else if (from != null && to != null) {
            result = auditLogRepository.findByOrgIdAndCreatedAtBetween(orgId, from, to, pageable);
        } else if (from != null) {
            result = auditLogRepository.findByOrgIdAndCreatedAtGreaterThanEqual(orgId, from, pageable);
        } else if (to != null) {
            result = auditLogRepository.findByOrgIdAndCreatedAtLessThanEqual(orgId, to, pageable);
        } else {
            result = auditLogRepository.findByOrgId(orgId, pageable);
        }

        return result.map(this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
