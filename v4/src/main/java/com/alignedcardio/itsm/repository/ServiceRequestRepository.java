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

    Optional<ServiceRequest> findByOrgIdAndId(UUID orgId, UUID id);

    Optional<ServiceRequest> findByNumberAndOrgId(String number, UUID orgId);
}
