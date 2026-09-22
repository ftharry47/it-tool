package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT MAX(a.createdAt) FROM AuditLog a " +
            "WHERE a.orgId = :orgId AND a.entityType = :entityType AND a.entityId = :entityId")
    OffsetDateTime findMaxCreatedAtByEntity(
            @Param("orgId") UUID orgId,
            @Param("entityType") String entityType,
            @Param("entityId") UUID entityId);

    List<AuditLog> findByOrgIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            UUID orgId, String entityType, UUID entityId);

    Page<AuditLog> findByOrgIdAndEntityTypeAndActionInOrderByCreatedAtDesc(
            UUID orgId, String entityType, java.util.Collection<String> actions, Pageable pageable);

    List<AuditLog> findByOrgIdAndEntityTypeAndEntityIdInAndActionIn(
            UUID orgId, String entityType, java.util.Collection<UUID> entityIds, java.util.Collection<String> actions);

    boolean existsByOrgIdAndEntityTypeAndEntityIdAndActionIn(
            UUID orgId, String entityType, UUID entityId, java.util.Collection<String> actions);

    // Native queries: before_state/after_state are jsonb, so HQL LIKE cannot
    // apply (Hibernate rejects 'like' on a non-string JDBC type). jsonb '->>'
    // extracts the assigneeId value as text directly.
    @Query(value = "SELECT * FROM audit_log WHERE org_id = :orgId AND entity_type = 'INCIDENT' " +
            "AND action IN ('ASSIGN','REASSIGN','AUTO_ESCALATE_TIER') " +
            "AND (before_state ->> 'assigneeId' = :assigneeId " +
            "     OR after_state ->> 'assigneeId' = :assigneeId)",
            nativeQuery = true)
    List<AuditLog> findIncidentAssigneeHistory(@Param("orgId") UUID orgId, @Param("assigneeId") String assigneeId);

    @Query(value = "SELECT * FROM audit_log WHERE org_id = :orgId AND entity_type = 'SERVICE_REQUEST' " +
            "AND action = 'TASK_ASSIGNED' " +
            "AND after_state ->> 'assigneeId' = :assigneeId",
            nativeQuery = true)
    List<AuditLog> findServiceRequestAssigneeHistory(@Param("orgId") UUID orgId, @Param("assigneeId") String assigneeId);

    // Viewer-scoped escalation check: was this incident tier-escalated AWAY
    // FROM this user (i.e. they were the assignee in the escalation's
    // before_state)? Incident-level "has ever been escalated" over-blocks
    // every subsequent assignee; this scopes the freeze to the right agent.
    @Query(value = "SELECT EXISTS(SELECT 1 FROM audit_log WHERE org_id = :orgId " +
            "AND entity_type = 'INCIDENT' AND entity_id = :entityId " +
            "AND action IN ('ESCALATE_TIER','AUTO_ESCALATE_TIER') " +
            "AND before_state ->> 'assigneeId' = :viewerId)",
            nativeQuery = true)
    boolean existsTierEscalationAwayFrom(@Param("orgId") UUID orgId,
                                         @Param("entityId") UUID entityId,
                                         @Param("viewerId") String viewerId);
}
