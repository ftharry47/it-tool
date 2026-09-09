package com.alignedcardio.itsm.api.servicerequest;

import com.alignedcardio.itsm.entity.FulfillmentTask;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record MyTaskResponse(
        UUID taskId,
        String description,
        int sequenceOrder,
        FulfillmentTask.Status status,
        LocalDate expectedDeliveryDate,
        UUID serviceRequestId,
        String serviceRequestNumber,
        String catalogItemName,
        String requesterName,
        OffsetDateTime assignedAt
) {
}
