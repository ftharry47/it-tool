package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.repository.WorkflowTransitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowTransitionValidatorTest {

    @Mock
    private WorkflowTransitionRepository repository;

    @InjectMocks
    private WorkflowTransitionValidator validator;

    @Test
    void legalTransitionPasses() {
        UUID workflowId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        when(repository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, from, to))
                .thenReturn(Optional.of(mock(com.alignedcardio.itsm.entity.WorkflowTransition.class)));

        assertDoesNotThrow(() -> validator.validate(workflowId, from, to));
        assertTrue(validator.isAllowed(workflowId, from, to));
    }

    @Test
    void illegalTransitionIsRejected() {
        UUID workflowId = UUID.randomUUID();
        UUID from = UUID.randomUUID();
        UUID to = UUID.randomUUID();

        when(repository.findByWorkflowIdAndFromStatusIdAndToStatusId(workflowId, from, to))
                .thenReturn(Optional.empty());

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> validator.validate(workflowId, from, to));
        assertTrue(e.getMessage().contains("Illegal workflow transition"));
        assertFalse(validator.isAllowed(workflowId, from, to));
    }
}
