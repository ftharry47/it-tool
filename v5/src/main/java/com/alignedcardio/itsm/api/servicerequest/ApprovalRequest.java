package com.alignedcardio.itsm.api.servicerequest;

public record ApprovalRequest(
        String comment,
        boolean approve
) {
}
