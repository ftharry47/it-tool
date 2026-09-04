package com.alignedcardio.itsm.api.user;

import java.util.List;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String jobTitle,
        String department,
        boolean active,
        List<String> roles
) {
}
