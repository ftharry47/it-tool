package com.alignedcardio.itsm.api.user;

import jakarta.validation.constraints.NotBlank;

public record UserRoleUpdateRequest(
        @NotBlank String roleName
) {
}
