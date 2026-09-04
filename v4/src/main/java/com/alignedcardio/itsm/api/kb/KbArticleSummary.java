package com.alignedcardio.itsm.api.kb;

import com.alignedcardio.itsm.entity.KbArticle;

import java.util.UUID;

public record KbArticleSummary(
        UUID id,
        String number,
        String title,
        String category,
        KbArticle.Status status,
        int viewCount,
        int helpfulCount,
        int version
) {
}
