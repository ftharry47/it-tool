package com.alignedcardio.itsm.api.incident;

import java.util.UUID;

public record IncidentAttachmentResponse(
        UUID id,
        String fileName,
        String contentType,
        Long sizeBytes,
        String blobUrl
) {
}
