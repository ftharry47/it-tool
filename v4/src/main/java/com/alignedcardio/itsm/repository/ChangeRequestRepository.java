package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    Optional<ChangeRequest> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ChangeRequest> findByNumberAndOrgId(Long number, UUID orgId);

    List<ChangeRequest> findByOrgIdAndStatusIn(UUID orgId, List<ChangeRequest.Status> statuses);
}
