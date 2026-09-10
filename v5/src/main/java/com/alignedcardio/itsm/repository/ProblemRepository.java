package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Problem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    List<Problem> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<Problem> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<Problem> findByNumberAndOrgId(String number, UUID orgId);

    List<Problem> findByOrgIdAndAssigneeIdOrderByCreatedAtDesc(UUID orgId, UUID assigneeId);

    @Query("SELECT p FROM Problem p WHERE p.orgId = ?1 AND p.deletedAt IS NULL " +
            "AND (lower(p.number) LIKE lower(concat('%', ?2, '%')) OR lower(p.title) LIKE lower(concat('%', ?2, '%'))) " +
            "ORDER BY p.createdAt DESC")
    List<Problem> searchByText(UUID orgId, String query, Pageable pageable);
}
