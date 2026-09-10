package com.alignedcardio.itsm.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Shared display formatting for user-facing dates/times in notification text
 * (email bodies, push bodies, in-app messages).
 *
 * All persisted timestamps are UTC (timestamptz). Anything rendered into a
 * human-readable string is converted to US Eastern Time (America/New_York,
 * auto-adjusts EST/EDT) and formatted MM/DD/YYYY — matching the frontend's
 * src/lib/date.ts so a timestamp means the same wall-clock time in-app, in
 * email, and in push.
 */
public final class DateFormats {

    public static final ZoneId DISPLAY_ZONE = ZoneId.of("America/New_York");

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy, h:mm a");

    private DateFormats() {
    }

    /** "MM/DD/YYYY" for a date-only value (already zone-less). */
    public static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }

    /** "MM/DD/YYYY" for a UTC timestamp, rendered in Eastern time. */
    public static String formatDate(OffsetDateTime ts) {
        return ts == null ? "" : ts.atZoneSameInstant(DISPLAY_ZONE).format(DATE);
    }

    /** "MM/DD/YYYY, h:mm AM/PM" for a UTC timestamp, rendered in Eastern time. */
    public static String formatDateTime(OffsetDateTime ts) {
        return ts == null ? "" : ts.atZoneSameInstant(DISPLAY_ZONE).format(DATE_TIME);
    }
}
