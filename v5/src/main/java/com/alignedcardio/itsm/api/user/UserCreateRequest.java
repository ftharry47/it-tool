package com.alignedcardio.itsm.api.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
        @NotBlank @Email String email,
        String displayName,
        String jobTitle,
        String department,
        @NotBlank String roleName
) {
}
