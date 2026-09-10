package com.alignedcardio.itsm.api.project;

import java.util.UUID;

public record IssueTypeResponse(
        UUID id,
        String name,
        String description,
        String icon,
        String color
) {
}
