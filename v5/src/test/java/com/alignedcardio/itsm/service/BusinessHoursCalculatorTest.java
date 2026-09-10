package com.alignedcardio.itsm.service;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessHoursCalculatorTest {

    private final BusinessHoursCalculator calculator = new BusinessHoursCalculator();

    private static final String WORKING_HOURS = """
            {
              "monday": {"start": "09:00", "end": "17:00"},
              "tuesday": {"start": "09:00", "end": "17:00"},
              "wednesday": {"start": "09:00", "end": "17:00"},
              "thursday": {"start": "09:00", "end": "17:00"},
              "friday": {"start": "09:00", "end": "17:00"}
            }
            """;

    private static final String HOLIDAYS = "[\"2026-01-01\"]";

    @Test
    void dueDateSkipsWeekend() {
        // Friday 2026-01-02 16:00 UTC, target 120 minutes. 60 minutes fit on Friday,
        // the remaining 60 minutes run on Monday => 10:00.
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 2, 16, 0, 0, 0, ZoneId.of("UTC"));

        ZonedDateTime due = calculator.addBusinessMinutes("UTC", WORKING_HOURS, "[]", start, 120);

        assertEquals(2026, due.getYear());
        assertEquals(1, due.getMonthValue());
        assertEquals(5, due.getDayOfMonth()); // Monday
        assertEquals(10, due.getHour());
        assertEquals(0, due.getMinute());
    }

    @Test
    void dueDateSkipsHoliday() {
        // New Year's Day (2026-01-01) is a holiday. Starting on the holiday itself should
        // snap to the next working day (Friday 2026-01-02 09:00) and add 60 minutes => 10:00.
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 1, 9, 0, 0, 0, ZoneId.of("UTC"));

        ZonedDateTime due = calculator.addBusinessMinutes("UTC", WORKING_HOURS, HOLIDAYS, start, 60);

        assertEquals(2026, due.getYear());
        assertEquals(1, due.getMonthValue());
        assertEquals(2, due.getDayOfMonth()); // Friday
        assertEquals(10, due.getHour());
        assertEquals(0, due.getMinute());
    }

    @Test
    void elapsedBusinessMinutesRespectsWorkingHours() {
        // Friday 09:00 to Monday 11:00 should be 2 working days (Fri 9-17 => 480, Mon 9-11 => 120)
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 2, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime end = ZonedDateTime.of(2026, 1, 5, 11, 0, 0, 0, ZoneId.of("UTC"));

        int minutes = calculator.elapsedBusinessMinutes("UTC", WORKING_HOURS, "[]", start, end);

        assertEquals(600, minutes);
    }

    @Test
    void startOutsideWorkingHoursSnapsToNextStart() {
        // Friday 18:00 (after hours), add 60 minutes => Monday 10:00
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 2, 18, 0, 0, 0, ZoneId.of("UTC"));

        ZonedDateTime due = calculator.addBusinessMinutes("UTC", WORKING_HOURS, "[]", start, 60);

        assertEquals(2026, due.getYear());
        assertEquals(1, due.getMonthValue());
        assertEquals(5, due.getDayOfMonth());
        assertEquals(10, due.getHour());
        assertEquals(0, due.getMinute());
    }

    @Test
    void addZeroMinutesReturnsSameWorkingStart() {
        // Sunday start should snap to Monday 09:00 even with 0 minutes
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 4, 12, 0, 0, 0, ZoneId.of("UTC"));

        ZonedDateTime due = calculator.addBusinessMinutes("UTC", WORKING_HOURS, "[]", start, 0);

        assertEquals(2026, due.getYear());
        assertEquals(1, due.getMonthValue());
        assertEquals(5, due.getDayOfMonth());
        assertEquals(9, due.getHour());
        assertEquals(0, due.getMinute());
    }

    @Test
    void elapsedWhenEndIsBeforeStartIsZero() {
        ZonedDateTime start = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime end = ZonedDateTime.of(2026, 1, 2, 9, 0, 0, 0, ZoneId.of("UTC"));

        int minutes = calculator.elapsedBusinessMinutes("UTC", WORKING_HOURS, "[]", start, end);

        assertEquals(0, minutes);
    }

    @Test
    void holidaysAreSkippedInElapsed() {
        // Wednesday 2025-12-31 09:00 to Monday 2026-01-05 09:00, with Jan 1 a holiday.
        // Working time = Wed 9-17 (480) + Fri 9-17 (480) + Mon 0 = 960 minutes.
        ZonedDateTime start = ZonedDateTime.of(2025, 12, 31, 9, 0, 0, 0, ZoneId.of("UTC"));
        ZonedDateTime end = ZonedDateTime.of(2026, 1, 5, 9, 0, 0, 0, ZoneId.of("UTC"));

        int minutes = calculator.elapsedBusinessMinutes("UTC", WORKING_HOURS, HOLIDAYS, start, end);

        assertEquals(960, minutes);
    }

    @Test
    void newYearsHolidayIsNotTreatedAsWorkingDay() {
        // Wednesday 2025-12-31 16:00, add 120 minutes. 60 minutes fit on Wednesday,
        // the remaining 60 minutes run on Friday 2026-01-02 (Jan 1 is a holiday) => 10:00.
        ZonedDateTime start = ZonedDateTime.of(2025, 12, 31, 16, 0, 0, 0, ZoneId.of("UTC"));

        ZonedDateTime due = calculator.addBusinessMinutes("UTC", WORKING_HOURS, HOLIDAYS, start, 120);

        assertEquals(2026, due.getYear());
        assertEquals(1, due.getMonthValue());
        assertEquals(2, due.getDayOfMonth()); // Friday
        assertEquals(10, due.getHour());
        assertEquals(0, due.getMinute());
    }
}
