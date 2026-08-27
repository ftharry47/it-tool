package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.repository.WorkflowTransitionRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class WorkflowTransitionValidator {

    private final WorkflowTransitionRepository workflowTransitionRepository;

    public WorkflowTransitionValidator(WorkflowTransitionRepository workflowTransitionRepository) {
        this.workflowTransitionRepository = workflowTransitionRepository;
    }

    public void validate(UUID workflowId, UUID fromStatusId, UUID toStatusId) {
        if (fromStatusId == null || toStatusId == null) {
            throw new IllegalStateException("Both from and to status ids are required");
        }

        if (fromStatusId.equals(toStatusId)) {
            return;
        }

        if (workflowTransitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId).isEmpty()) {
            throw new IllegalStateException("Illegal workflow transition: no transition defined from " + fromStatusId + " to " + toStatusId);
        }
    }

    public boolean isAllowed(UUID workflowId, UUID fromStatusId, UUID toStatusId) {
        if (fromStatusId == null || toStatusId == null) {
            return false;
        }
        if (fromStatusId.equals(toStatusId)) {
            return true;
        }
        return workflowTransitionRepository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, fromStatusId, toStatusId).isPresent();
    }
}
