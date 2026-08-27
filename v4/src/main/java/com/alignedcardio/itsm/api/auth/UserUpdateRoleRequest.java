package com.alignedcardio.itsm.api.auth;

import jakarta.validation.constraints.NotBlank;

public record UserUpdateRoleRequest(
        @NotBlank String role
) {
}
