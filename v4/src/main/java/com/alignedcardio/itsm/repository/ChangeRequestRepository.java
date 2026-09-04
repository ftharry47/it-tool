package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ChangeRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<ChangeRequest> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ChangeRequest> findByNumberAndOrgId(String number, UUID orgId);

    List<ChangeRequest> findByOrgIdAndStatusIn(UUID orgId, List<ChangeRequest.Status> statuses);

    @Query("SELECT c FROM ChangeRequest c WHERE c.orgId = ?1 AND c.deletedAt IS NULL " +
            "AND (lower(c.number) LIKE lower(concat('%', ?2, '%')) OR lower(c.title) LIKE lower(concat('%', ?2, '%'))) " +
            "ORDER BY c.createdAt DESC")
    List<ChangeRequest> searchByText(UUID orgId, String query, Pageable pageable);
}
