package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    List<Workflow> findByOrgIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID orgId);

    Optional<Workflow> findByOrgIdAndIdAndDeletedAtIsNull(UUID orgId, UUID id);
}
