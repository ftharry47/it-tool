package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Incident;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentStatusMachineTest {

    @Test
    void newToClosedIsIllegal() {
        assertThrows(IllegalStateException.class,
                () -> IncidentStatusMachine.validate(Incident.Status.NEW, Incident.Status.CLOSED));
    }

    @Test
    void newToInProgressIsLegal() {
        assertDoesNotThrow(
                () -> IncidentStatusMachine.validate(Incident.Status.NEW, Incident.Status.IN_PROGRESS));
    }

    @Test
    void resolvedToReopenedIsLegal() {
        assertDoesNotThrow(
                () -> IncidentStatusMachine.validate(Incident.Status.RESOLVED, Incident.Status.REOPENED));
    }

    @Test
    void closedIsTerminal() {
        assertThrows(IllegalStateException.class,
                () -> IncidentStatusMachine.validate(Incident.Status.CLOSED, Incident.Status.NEW));
    }
}
