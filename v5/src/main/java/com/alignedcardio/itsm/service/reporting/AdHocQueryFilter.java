package com.alignedcardio.itsm.service.reporting;

public record AdHocQueryFilter(
        String field,
        String op,
        String value
) {
}
