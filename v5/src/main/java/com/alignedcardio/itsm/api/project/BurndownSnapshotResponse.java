package com.alignedcardio.itsm.api.project;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BurndownSnapshotResponse(
        UUID id,
        UUID sprintId,
        OffsetDateTime snapshotDate,
        int totalPoints,
        int remainingPoints,
        int openIssues
) {
}
