package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ChangeApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChangeApprovalRepository extends JpaRepository<ChangeApproval, UUID> {

    List<ChangeApproval> findByChangeRequestIdOrderBySequenceOrderAsc(UUID changeRequestId);

    Optional<ChangeApproval> findByChangeRequestIdAndSequenceOrder(UUID changeRequestId, int sequenceOrder);

    boolean existsByChangeRequestIdAndSequenceOrderAndStatus(UUID changeRequestId, int sequenceOrder, ChangeApproval.Status status);
}
