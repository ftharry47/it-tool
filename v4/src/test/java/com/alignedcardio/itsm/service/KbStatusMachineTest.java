package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.KbArticle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KbStatusMachineTest {

    @Test
    void draftToPendingReviewIsLegal() {
        assertDoesNotThrow(
                () -> KbStatusMachine.validate(KbArticle.Status.DRAFT, KbArticle.Status.PENDING_REVIEW));
    }

    @Test
    void pendingReviewToPublishedIsLegal() {
        assertDoesNotThrow(
                () -> KbStatusMachine.validate(KbArticle.Status.PENDING_REVIEW, KbArticle.Status.PUBLISHED));
    }

    @Test
    void publishedToArchivedIsLegal() {
        assertDoesNotThrow(
                () -> KbStatusMachine.validate(KbArticle.Status.PUBLISHED, KbArticle.Status.ARCHIVED));
    }

    @Test
    void publishedToDraftIsIllegal() {
        assertThrows(IllegalStateException.class,
                () -> KbStatusMachine.validate(KbArticle.Status.PUBLISHED, KbArticle.Status.DRAFT));
    }
}
