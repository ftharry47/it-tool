package com.alignedcardio.itsm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class BusinessHoursCalculator {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ZonedDateTime addBusinessMinutes(String timezone, String workingHoursJson, String holidaysJson,
                                            ZonedDateTime start, int minutes) {
        ZoneId zone = ZoneId.of(timezone);
        WorkingHours wh = parseWorkingHours(workingHoursJson);
        Set<LocalDate> holidays = parseHolidays(holidaysJson);

        ZonedDateTime cursor = start.withZoneSameInstant(zone);

        // If start is outside working time, jump to the next working start
        cursor = nextWorkingStart(cursor, wh, holidays, zone);

        int remaining = minutes;
        while (remaining > 0) {
            DayOfWeek day = cursor.getDayOfWeek();
            WorkWindow window = wh.get(day);
            if (window == null || holidays.contains(cursor.toLocalDate())) {
                cursor = moveToNextDayStart(cursor, wh, holidays, zone);
                continue;
            }

            LocalTime currentTime = cursor.toLocalTime();
            LocalTime endTime = window.end;

            if (currentTime.isBefore(window.start)) {
                currentTime = window.start;
                cursor = cursor.with(currentTime);
            }

            if (!currentTime.isBefore(endTime)) {
                cursor = moveToNextDayStart(cursor, wh, holidays, zone);
                continue;
            }

            int availableUntilEnd = (int) java.time.Duration.between(currentTime, endTime).toMinutes();

            if (remaining <= availableUntilEnd) {
                return cursor.plusMinutes(remaining);
            }

            remaining -= availableUntilEnd;
            cursor = cursor.with(endTime);
            cursor = moveToNextDayStart(cursor, wh, holidays, zone);
        }

        return cursor;
    }

    public int elapsedBusinessMinutes(String timezone, String workingHoursJson, String holidaysJson,
                                      ZonedDateTime start, ZonedDateTime end) {
        if (end.isBefore(start)) {
            return 0;
        }
        ZoneId zone = ZoneId.of(timezone);
        WorkingHours wh = parseWorkingHours(workingHoursJson);
        Set<LocalDate> holidays = parseHolidays(holidaysJson);

        ZonedDateTime cursor = start.withZoneSameInstant(zone);
        ZonedDateTime finish = end.withZoneSameInstant(zone);

        cursor = nextWorkingStart(cursor, wh, holidays, zone);

        int total = 0;
        while (cursor.isBefore(finish)) {
            DayOfWeek day = cursor.getDayOfWeek();
            WorkWindow window = wh.get(day);
            if (window == null || holidays.contains(cursor.toLocalDate())) {
                cursor = moveToNextDayStart(cursor, wh, holidays, zone);
                continue;
            }

            LocalTime currentTime = cursor.toLocalTime();
            if (currentTime.isBefore(window.start)) {
                currentTime = window.start;
            }
            LocalTime endTime = window.end;

            if (currentTime.isAfter(endTime)) {
                cursor = moveToNextDayStart(cursor, wh, holidays, zone);
                continue;
            }

            LocalTime effectiveEnd = java.time.LocalTime.from(finish.toLocalTime());
            if (finish.toLocalDate().equals(cursor.toLocalDate()) && effectiveEnd.isBefore(endTime)) {
                endTime = effectiveEnd;
            }

            if (currentTime.isAfter(endTime)) {
                cursor = moveToNextDayStart(cursor, wh, holidays, zone);
                continue;
            }

            total += (int) java.time.Duration.between(currentTime, endTime).toMinutes();

            if (finish.toLocalDate().equals(cursor.toLocalDate())) {
                break;
            }

            cursor = cursor.with(endTime);
            cursor = moveToNextDayStart(cursor, wh, holidays, zone);
        }

        return total;
    }

    private ZonedDateTime nextWorkingStart(ZonedDateTime cursor, WorkingHours wh, Set<LocalDate> holidays, ZoneId zone) {
        for (int i = 0; i < 365; i++) {
            DayOfWeek day = cursor.getDayOfWeek();
            WorkWindow window = wh.get(day);
            if (window != null && !holidays.contains(cursor.toLocalDate())) {
                if (cursor.toLocalTime().isBefore(window.end)) {
                    if (cursor.toLocalTime().isBefore(window.start)) {
                        return cursor.with(window.start);
                    }
                    return cursor;
                }
            }
            cursor = cursor.plusDays(1).with(LocalTime.MIN);
        }
        throw new IllegalStateException("Could not find a working day within one year");
    }

    private ZonedDateTime moveToNextDayStart(ZonedDateTime cursor, WorkingHours wh, Set<LocalDate> holidays, ZoneId zone) {
        ZonedDateTime next = cursor.plusDays(1).with(LocalTime.MIN);
        for (int i = 0; i < 365; i++) {
            DayOfWeek day = next.getDayOfWeek();
            WorkWindow window = wh.get(day);
            if (window != null && !holidays.contains(next.toLocalDate())) {
                return next.with(window.start);
            }
            next = next.plusDays(1).with(LocalTime.MIN);
        }
        throw new IllegalStateException("Could not find a working day within one year");
    }

    private WorkingHours parseWorkingHours(String json) {
        WorkingHours wh = new WorkingHours();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return defaultWorkingHours();
            }
            for (Iterator<String> it = root.fieldNames(); it.hasNext(); ) {
                String day = it.next();
                JsonNode node = root.get(day);
                if (node != null && !node.isNull() && node.has("start") && node.has("end")) {
                    LocalTime start = LocalTime.parse(node.get("start").asText());
                    LocalTime end = LocalTime.parse(node.get("end").asText());
                    wh.put(DayOfWeek.valueOf(day.toUpperCase()), new WorkWindow(start, end));
                }
            }
        } catch (Exception e) {
            return defaultWorkingHours();
        }
        if (wh.isEmpty()) {
            return defaultWorkingHours();
        }
        return wh;
    }

    private Set<LocalDate> parseHolidays(String json) {
        Set<LocalDate> holidays = new HashSet<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root != null && root.isArray()) {
                for (JsonNode node : root) {
                    holidays.add(LocalDate.parse(node.asText()));
                }
            }
        } catch (Exception e) {
            // ignore malformed holidays
        }
        return holidays;
    }

    private WorkingHours defaultWorkingHours() {
        WorkingHours wh = new WorkingHours();
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                wh.put(day, new WorkWindow(LocalTime.of(9, 0), LocalTime.of(17, 0)));
            }
        }
        return wh;
    }

    private static class WorkingHours extends java.util.EnumMap<DayOfWeek, WorkWindow> {
        WorkingHours() {
            super(DayOfWeek.class);
        }
    }

    private record WorkWindow(LocalTime start, LocalTime end) {
    }
}
