package com.alignedcardio.itsm.api.user;

public record AdSyncResult(
        int added,
        int updated,
        int skipped,
        int errors
) {
}
