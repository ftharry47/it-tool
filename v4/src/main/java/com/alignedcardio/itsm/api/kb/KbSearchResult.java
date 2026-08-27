package com.alignedcardio.itsm.api.kb;

import java.util.UUID;

public record KbSearchResult(
        UUID id,
        Long number,
        String title,
        String category,
        double rank
) {
}
