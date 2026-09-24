package com.alignedcardio.itsm.api.admin;

import com.alignedcardio.itsm.entity.SlaPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SlaPolicyRequest(
        @NotBlank String name,
        @NotNull SlaPolicy.AppliesTo appliesTo,
        String priorityFilter,
        String workflowType,
        @NotNull Integer responseTargetMinutes,
        @NotNull Integer resolutionTargetMinutes,
        UUID businessHoursCalendarId
) {
}
