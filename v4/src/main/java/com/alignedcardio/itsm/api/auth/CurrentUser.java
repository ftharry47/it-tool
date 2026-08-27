package com.alignedcardio.itsm.api.auth;

import java.util.List;
import java.util.UUID;

public record CurrentUser(
        UUID id,
        String objectId,
        String email,
        String displayName,
        String jobTitle,
        String department,
        List<String> roles,
        boolean isActive,
        boolean mfaEnabled,
        UUID managerId
) {
}
