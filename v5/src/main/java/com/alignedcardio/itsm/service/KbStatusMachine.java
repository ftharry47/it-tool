package com.alignedcardio.itsm.service;

import com.alignedcardio.itsm.entity.KbArticle;

import java.util.Map;
import java.util.Set;

public final class KbStatusMachine {

    private static final Map<KbArticle.Status, Set<KbArticle.Status>> ALLOWED = Map.ofEntries(
            Map.entry(KbArticle.Status.DRAFT, Set.of(KbArticle.Status.PENDING_REVIEW)),
            Map.entry(KbArticle.Status.PENDING_REVIEW, Set.of(KbArticle.Status.PUBLISHED, KbArticle.Status.DRAFT)),
            Map.entry(KbArticle.Status.PUBLISHED, Set.of(KbArticle.Status.ARCHIVED)),
            Map.entry(KbArticle.Status.ARCHIVED, Set.of(KbArticle.Status.DRAFT))
    );

    private KbStatusMachine() {
    }

    public static void validate(KbArticle.Status from, KbArticle.Status to) {
        if (from == to) {
            return;
        }
        if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            return;
        }
        throw new IllegalStateException("Illegal KB article status transition: " + from + " -> " + to);
    }
}
