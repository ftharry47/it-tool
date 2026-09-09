package com.alignedcardio.itsm.api.team;

import java.util.UUID;

public record TeamMemberInfo(
        UUID userId,
        String displayName,
        String email
) {
}
