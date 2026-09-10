package com.alignedcardio.itsm.repository;

import com.alignedcardio.itsm.entity.WorkflowTransition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, UUID> {

    List<WorkflowTransition> findByWorkflowId(UUID workflowId);

    Optional<WorkflowTransition> findByWorkflowIdAndFromStatusIdAndToStatusId(UUID workflowId, UUID fromStatusId, UUID toStatusId);
}
