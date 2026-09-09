package com.alignedcardio.itsm.api.kb;

import com.alignedcardio.itsm.entity.KbArticle;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KbArticleResponse(
        UUID id,
        String number,
        String title,
        String category,
        String body,
        KbArticle.Status status,
        UUID authorId,
        String authorName,
        int viewCount,
        int helpfulCount,
        int notHelpfulCount,
        int version,
        OffsetDateTime publishedAt,
        OffsetDateTime archivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        Boolean myVote
) {
}
