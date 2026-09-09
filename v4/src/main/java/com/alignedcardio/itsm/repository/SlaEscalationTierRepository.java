package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.SlaEscalationTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SlaEscalationTierRepository extends JpaRepository<SlaEscalationTier, UUID> {

    List<SlaEscalationTier> findByPolicyIdOrderByLevelAsc(UUID policyId);
}
