package com.alignedcardio.itsm.api.auth;

import java.util.UUID;

public record UpdateUserRequest(
        Boolean isActive,
        Boolean mfaEnabled,
        UUID managerId
) {
}
