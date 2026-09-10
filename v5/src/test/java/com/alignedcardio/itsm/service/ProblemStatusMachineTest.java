package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Problem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProblemStatusMachineTest {

    @Test
    void newToInvestigatingIsLegal() {
        assertDoesNotThrow(
                () -> ProblemStatusMachine.validate(Problem.Status.NEW, Problem.Status.INVESTIGATING));
    }

    @Test
    void knownErrorToResolvedIsLegal() {
        assertDoesNotThrow(
                () -> ProblemStatusMachine.validate(Problem.Status.KNOWN_ERROR, Problem.Status.RESOLVED));
    }

    @Test
    void resolvedToClosedIsLegal() {
        assertDoesNotThrow(
                () -> ProblemStatusMachine.validate(Problem.Status.RESOLVED, Problem.Status.CLOSED));
    }

    @Test
    void newToClosedIsIllegal() {
        assertThrows(IllegalStateException.class,
                () -> ProblemStatusMachine.validate(Problem.Status.NEW, Problem.Status.CLOSED));
    }
}
