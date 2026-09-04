package com.alignedcardio.itsm.api.kb;

import java.util.UUID;

public record KbSearchResult(
        UUID id,
        String number,
        String title,
        String category,
        double rank
) {
}
