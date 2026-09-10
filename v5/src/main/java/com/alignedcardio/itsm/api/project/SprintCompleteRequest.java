package com.alignedcardio.itsm.api.project;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SprintCompleteRequest(
        @NotNull Destination destination,
        UUID nextSprintId
) {
    public enum Destination { BACKLOG, NEXT_SPRINT }
}
