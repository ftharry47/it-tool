package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Incident;

import java.util.Map;
import java.util.Set;

public final class IncidentStatusMachine {

    private static final Map<Incident.Status, Set<Incident.Status>> ALLOWED = Map.ofEntries(
            Map.entry(Incident.Status.NEW, Set.of(Incident.Status.IN_PROGRESS, Incident.Status.ON_HOLD)),
            Map.entry(Incident.Status.IN_PROGRESS, Set.of(Incident.Status.ON_HOLD, Incident.Status.RESOLVED)),
            Map.entry(Incident.Status.ON_HOLD, Set.of(Incident.Status.IN_PROGRESS)),
            Map.entry(Incident.Status.RESOLVED, Set.of(Incident.Status.CLOSED, Incident.Status.REOPENED)),
            Map.entry(Incident.Status.CLOSED, Set.of(Incident.Status.REOPENED)),
            Map.entry(Incident.Status.REOPENED, Set.of(Incident.Status.IN_PROGRESS))
    );

    private IncidentStatusMachine() {
    }

    public static void validate(Incident.Status from, Incident.Status to) {
        if (from == to) {
            return;
        }
        if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            return;
        }
        throw new IllegalStateException("Illegal status transition: " + from + " -> " + to);
    }
}
