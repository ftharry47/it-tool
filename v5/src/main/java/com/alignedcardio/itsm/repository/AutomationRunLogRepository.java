package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.AutomationRunLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AutomationRunLogRepository extends JpaRepository<AutomationRunLog, UUID> {

    List<AutomationRunLog> findByOrgIdAndRuleIdOrderByCreatedAtDesc(UUID orgId, UUID ruleId);
}
