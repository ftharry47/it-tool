package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.api.auth.AuditLogResponse;
import com.alignedcardio.itsm.entity.AuditLog;
import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.UserRole;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final int PAGE_SIZE = 25;

    private final AuditLogRepository auditLogRepository;
    private final AppUserRepository appUserRepository;

    public AuditLogService(AuditLogRepository auditLogRepository,
                           AppUserRepository appUserRepository) {
        this.auditLogRepository = auditLogRepository;
        this.appUserRepository = appUserRepository;
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

    public AuditLogResponse toResponse(AuditLog log) {
        AppUser actor = log.getActorUserId() == null
                ? null
                : appUserRepository.findById(log.getActorUserId()).orElse(null);
        return new AuditLogResponse(
                log.getId(),
                log.getActorUserId(),
                actor == null ? null : actor.getDisplayName(),
                actor == null ? null : resolveActorRole(actor),
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getBeforeState(),
                log.getAfterState(),
                log.getIpAddress(),
                log.getCreatedAt());
    }

    private String resolveActorRole(AppUser user) {
        if (user.getUserRoles() == null) {
            return null;
        }
        return user.getUserRoles().stream()
                .map(UserRole::getRole)
                .filter(role -> role != null && role.getName() != null)
                .map(role -> role.getName())
                .min(this::roleOrder)
                .orElse(null);
    }

    private int roleOrder(String a, String b) {
        List<String> order = List.of("SUPER_ADMIN", "ADMIN", "TEAM_LEAD", "AGENT", "END_USER");
        int ia = order.indexOf(a);
        int ib = order.indexOf(b);
        if (ia == -1) ia = Integer.MAX_VALUE;
        if (ib == -1) ib = Integer.MAX_VALUE;
        return Integer.compare(ia, ib);
    }
}
