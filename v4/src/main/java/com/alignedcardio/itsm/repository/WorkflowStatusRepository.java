package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.WorkflowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowStatusRepository extends JpaRepository<WorkflowStatus, UUID> {

    List<WorkflowStatus> findByWorkflowIdOrderByDisplayOrderAsc(UUID workflowId);

    Optional<WorkflowStatus> findByWorkflowIdAndId(UUID workflowId, UUID id);
}
