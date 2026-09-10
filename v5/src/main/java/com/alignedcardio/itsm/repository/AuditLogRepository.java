package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByOrgIdAndEntityTypeAndCreatedAtBetween(
            UUID orgId, String entityType, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByOrgIdAndEntityTypeAndCreatedAtGreaterThanEqual(
            UUID orgId, String entityType, OffsetDateTime from, Pageable pageable);

    Page<AuditLog> findByOrgIdAndEntityTypeAndCreatedAtLessThanEqual(
            UUID orgId, String entityType, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByOrgIdAndEntityType(
            UUID orgId, String entityType, Pageable pageable);

    Page<AuditLog> findByOrgIdAndCreatedAtBetween(
            UUID orgId, OffsetDateTime from, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByOrgIdAndCreatedAtGreaterThanEqual(
            UUID orgId, OffsetDateTime from, Pageable pageable);

    Page<AuditLog> findByOrgIdAndCreatedAtLessThanEqual(
            UUID orgId, OffsetDateTime to, Pageable pageable);

    Page<AuditLog> findByOrgId(UUID orgId, Pageable pageable);

    List<AuditLog> findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            UUID orgId, String entityType, UUID entityId);

    Page<AuditLog> findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
            UUID orgId, String entityType, java.util.Collection<String> actions, Pageable pageable);
}
