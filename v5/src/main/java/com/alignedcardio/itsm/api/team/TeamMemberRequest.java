package com.alignedcardio.itsm.api.team;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record TeamMemberRequest(
        @NotNull
        UUID userId
) {
}
