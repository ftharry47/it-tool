package com.alignedcardio.itsm.api.servicerequest;

import com.alignedcardio.itsm.entity.FulfillmentTask;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FulfillmentTaskResponse(
        UUID id,
        String description,
        int sequenceOrder,
        FulfillmentTask.Status status,
        UUID assigneeId,
        String assigneeName,
        OffsetDateTime completedAt,
        LocalDate expectedDeliveryDate,
        OffsetDateTime deliveredAt
) {
}
