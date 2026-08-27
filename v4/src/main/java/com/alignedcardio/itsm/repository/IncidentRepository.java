package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    List<Incident> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<Incident> findByOrgIdAndId(UUID orgId, UUID id);

    @Query(value = "SELECT * FROM incident WHERE org_id = ?1 AND deleted_at IS NULL " +
            "AND to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'')) " +
            "@@ plainto_tsquery('english', ?2) ORDER BY created_at DESC",
            nativeQuery = true)
    List<Incident> searchByText(UUID orgId, String query);
}
