package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.ServiceRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest, UUID> {

    List<ServiceRequest> findByOrgIdOrderByCreatedAtDesc(UUID orgId);

    List<ServiceRequest> findByOrgIdAndRequester_IdOrderByCreatedAtDesc(UUID orgId, UUID requesterId);

    List<ServiceRequest> findByOrgIdAndIdInOrderByCreatedAtDesc(UUID orgId, java.util.Collection<UUID> ids);

    Optional<ServiceRequest> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ServiceRequest> findByNumberAndOrgId(String number, UUID orgId);

    boolean existsByLocation_Id(UUID locationId);

    List<ServiceRequest> findByOrgIdAndStatusAndApprover_IdOrderByCreatedAtDesc(
            UUID orgId, ServiceRequest.Status status, UUID approverId);

    List<ServiceRequest> findByOrgIdAndApprover_IdAndApprovalDecisionAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID orgId, UUID approverId, ServiceRequest.ApprovalDecision approvalDecision);
}
