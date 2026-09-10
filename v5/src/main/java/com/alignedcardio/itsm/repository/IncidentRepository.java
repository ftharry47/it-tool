package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Incident;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<Incident> findByOrgIdAndId(UUID orgId, UUID id);

    List<Incident> findByOrgIdAndRequesterIdOrderByCreatedAtDesc(UUID orgId, UUID requesterId);

    @Query(value = "SELECT * FROM incident WHERE org_id = ?1 AND deleted_at IS NULL " +
            "AND (CAST(number AS text) ILIKE '%' || ?2 || '%' " +
            "OR title ILIKE '%' || ?2 || '%' " +
            "OR description ILIKE '%' || ?2 || '%' " +
            "OR to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')) " +
            "@@ plainto_tsquery('english', ?2)) ORDER BY created_at DESC LIMIT ?3",
            nativeQuery = true)
    List<Incident> searchByText(UUID orgId, String query, int limit);

    @Query(value = "SELECT * FROM incident WHERE org_id = ?1 AND requester_id = ?2 AND deleted_at IS NULL " +
            "AND (CAST(number AS text) ILIKE '%' || ?3 || '%' " +
            "OR title ILIKE '%' || ?3 || '%' " +
            "OR description ILIKE '%' || ?3 || '%' " +
            "OR to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')) " +
            "@@ plainto_tsquery('english', ?3)) ORDER BY created_at DESC LIMIT ?4",
            nativeQuery = true)
    List<Incident> searchByTextForRequester(UUID orgId, UUID requesterId, String query, int limit);

    @Query("SELECT i FROM Incident i WHERE i.orgId = ?1 AND i.status IN (?2) ORDER BY i.createdAt DESC")
    List<Incident> findByOrgIdAndStatusInOrderByCreatedAtDesc(UUID orgId, Collection<Incident.Status> statuses, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.orgId = ?1 AND i.assignee.id = ?2 AND i.status IN (?3) ORDER BY i.createdAt DESC")
    List<Incident> findByOrgIdAndAssigneeIdAndStatusInOrderByCreatedAtDesc(UUID orgId, UUID assigneeId, Collection<Incident.Status> statuses, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.orgId = ?1 AND i.assignee IS NULL AND i.status IN (?2) ORDER BY i.createdAt DESC")
    List<Incident> findByOrgIdAndAssigneeIsNullAndStatusInOrderByCreatedAtDesc(UUID orgId, Collection<Incident.Status> statuses, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.orgId = ?1 AND i.assignmentTeam.id = ?2 AND i.status IN (?3) ORDER BY i.createdAt DESC")
    List<Incident> findByOrgIdAndAssignmentTeam_IdAndStatusInOrderByCreatedAtDesc(UUID orgId, UUID teamId, Collection<Incident.Status> statuses, Pageable pageable);

    @Query("SELECT i FROM Incident i WHERE i.orgId = ?1 AND i.assignee.id = ?2 AND i.createdAt >= ?3 AND i.createdAt < ?4")
    List<Incident> findByOrgIdAndAssigneeIdAndCreatedAtBetween(UUID orgId, UUID assigneeId,
                                                             java.time.OffsetDateTime from, java.time.OffsetDateTime to);

    boolean existsByLocation_Id(UUID locationId);

    boolean existsByCategory_Id(UUID categoryId);
}
