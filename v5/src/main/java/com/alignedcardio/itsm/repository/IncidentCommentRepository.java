package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.IncidentComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface IncidentCommentRepository extends JpaRepository<IncidentComment, UUID> {

    List<IncidentComment> findByIncidentIdAndIsPublicTrueOrderByCreatedAtAsc(UUID incidentId);

    List<IncidentComment> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT MAX(c.createdAt) FROM IncidentComment c WHERE c.incident.id = :incidentId")
    java.time.OffsetDateTime findMaxCreatedAtByIncidentId(
            @org.springframework.data.repository.query.Param("incidentId") UUID incidentId);
}
