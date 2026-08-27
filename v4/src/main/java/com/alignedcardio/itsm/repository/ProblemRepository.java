package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    List<Problem> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<Problem> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<Problem> findByNumberAndOrgId(Long number, UUID orgId);
}
