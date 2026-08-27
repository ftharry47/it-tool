package com.alignedcardio.itsm.api.kb;

public record KbFeedbackRequest(
        boolean helpful,
        String comment
) {
}
