package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, UUID> {

    List<SlaPolicy> findByOrgId(UUID orgId);

    List<SlaPolicy> findByOrgIdAndAppliesTo(UUID orgId, SlaPolicy.AppliesTo appliesTo);

    Optional<SlaPolicy> findByIdAndOrgId(UUID id, UUID orgId);
}
