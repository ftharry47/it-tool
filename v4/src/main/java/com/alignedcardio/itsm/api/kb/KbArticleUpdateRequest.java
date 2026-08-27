package com.alignedcardio.itsm.api.kb;

import com.alignedcardio.itsm.entity.KbArticle;

public record KbArticleUpdateRequest(
        String title,
        String category,
        String body,
        KbArticle.Status status
) {
}
