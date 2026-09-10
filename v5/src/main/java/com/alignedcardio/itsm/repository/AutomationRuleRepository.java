package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AutomationRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {

    List<AutomationRule> findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID orgId);

    Optional<AutomationRule> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);

    List<AutomationRule> findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(
            UUID orgId, String triggerEntity, String triggerType);
}
