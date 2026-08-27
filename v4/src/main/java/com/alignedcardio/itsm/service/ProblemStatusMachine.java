package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.Problem;

import java.util.Map;
import java.util.Set;

public final class ProblemStatusMachine {

    private static final Map<Problem.Status, Set<Problem.Status>> ALLOWED = Map.ofEntries(
            Map.entry(Problem.Status.NEW, Set.of(Problem.Status.INVESTIGATING)),
            Map.entry(Problem.Status.INVESTIGATING, Set.of(Problem.Status.KNOWN_ERROR, Problem.Status.RESOLVED)),
            Map.entry(Problem.Status.KNOWN_ERROR, Set.of(Problem.Status.RESOLVED)),
            Map.entry(Problem.Status.RESOLVED, Set.of(Problem.Status.CLOSED, Problem.Status.INVESTIGATING)),
            Map.entry(Problem.Status.CLOSED, Set.of())
    );

    private ProblemStatusMachine() {
    }

    public static void validate(Problem.Status from, Problem.Status to) {
        if (from == to) {
            return;
        }
        if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            return;
        }
        throw new IllegalStateException("Illegal problem status transition: " + from + " -> " + to);
    }
}
