package com.alignedcardio.itsm.api.kb;

import java.time.OffsetDateTime;
import java.util.UUID;

public record KbArticleVersionResponse(
        UUID id,
        int version,
        String title,
        String category,
        String body,
        OffsetDateTime createdAt
) {
}
