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
            "OR to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')) " +
            "@@ plainto_tsquery('english', ?2)) ORDER BY created_at DESC LIMIT ?3",
            nativeQuery = true)
    List<Incident> searchByText(UUID orgId, String query, int limit);

    @Query(value = "SELECT * FROM incident WHERE org_id = ?1 AND requester_id = ?2 AND deleted_at IS NULL " +
            "AND (CAST(number AS text) ILIKE '%' || ?3 || '%' " +
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
}
