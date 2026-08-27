package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.ChangeRequest;

import java.util.Map;
import java.util.Set;

public final class ChangeStatusMachine {

    private static final Map<ChangeRequest.Status, Set<ChangeRequest.Status>> ALLOWED = Map.ofEntries(
            Map.entry(ChangeRequest.Status.DRAFT, Set.of(ChangeRequest.Status.PENDING_APPROVAL, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.PENDING_APPROVAL, Set.of(ChangeRequest.Status.APPROVED, ChangeRequest.Status.REJECTED, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.APPROVED, Set.of(ChangeRequest.Status.SCHEDULED, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.SCHEDULED, Set.of(ChangeRequest.Status.IN_PROGRESS, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.IN_PROGRESS, Set.of(ChangeRequest.Status.COMPLETED, ChangeRequest.Status.FAILED, ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.COMPLETED, Set.of()),
            Map.entry(ChangeRequest.Status.FAILED, Set.of(ChangeRequest.Status.ROLLED_BACK, ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.ROLLED_BACK, Set.of()),
            Map.entry(ChangeRequest.Status.REJECTED, Set.of(ChangeRequest.Status.CANCELLED)),
            Map.entry(ChangeRequest.Status.CANCELLED, Set.of())
    );

    private ChangeStatusMachine() {
    }

    public static void validate(ChangeRequest.Status from, ChangeRequest.Status to) {
        if (from == to) {
            return;
        }
        if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            return;
        }
        throw new IllegalStateException("Illegal change status transition: " + from + " -> " + to);
    }

    public static void validate(ChangeRequest change, ChangeRequest.Status to) {
        validate(change.getStatus(), to);

        if (to == ChangeRequest.Status.COMPLETED
                && change.getChangeType() == ChangeRequest.ChangeType.EMERGENCY
                && (change.getPostImplementationReview() == null || change.getPostImplementationReview().isBlank())) {
            throw new IllegalStateException("EMERGENCY change cannot be marked COMPLETED without post_implementation_review");
        }
    }
}
