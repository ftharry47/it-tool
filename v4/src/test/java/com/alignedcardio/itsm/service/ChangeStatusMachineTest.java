package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.ChangeRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangeStatusMachineTest {

    @Test
    void draftToPendingApprovalIsLegal() {
        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.DRAFT, ChangeRequest.Status.PENDING_APPROVAL));
    }

    @Test
    void approvedToScheduledIsLegal() {
        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.APPROVED, ChangeRequest.Status.SCHEDULED));
    }

    @Test
    void scheduledToInProgressIsLegal() {
        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.IN_PROGRESS));
    }

    @Test
    void draftToInProgressIsIllegal() {
        assertThrows(IllegalStateException.class,
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.DRAFT, ChangeRequest.Status.IN_PROGRESS));
    }

    @Test
    void inProgressToCancelledIsLegal() {
        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.IN_PROGRESS, ChangeRequest.Status.CANCELLED));
    }

    @Test
    void approvedToCancelledIsLegal() {
        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(ChangeRequest.Status.APPROVED, ChangeRequest.Status.CANCELLED));
    }

    @Test
    void emergencyCannotCompleteWithoutRetroReview() {
        ChangeRequest change = new ChangeRequest();
        change.setStatus(ChangeRequest.Status.IN_PROGRESS);
        change.setChangeType(ChangeRequest.ChangeType.EMERGENCY);
        change.setPostImplementationReview("   ");

        assertThrows(IllegalStateException.class,
                () -> ChangeStatusMachine.validate(change, ChangeRequest.Status.COMPLETED));
    }

    @Test
    void emergencyCanCompleteWithRetroReview() {
        ChangeRequest change = new ChangeRequest();
        change.setStatus(ChangeRequest.Status.IN_PROGRESS);
        change.setChangeType(ChangeRequest.ChangeType.EMERGENCY);
        change.setPostImplementationReview("Conducted rollback after-hours. No customer impact.");

        assertDoesNotThrow(
                () -> ChangeStatusMachine.validate(change, ChangeRequest.Status.COMPLETED));
    }
}
