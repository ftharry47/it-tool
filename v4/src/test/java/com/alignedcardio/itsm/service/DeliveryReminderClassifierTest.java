package com.alignedcardio.itsm.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryReminderClassifierTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 5);

    @Test
    void threeDaysAway() {
        assertEquals(DeliveryReminderClassifier.Reminder.THREE_DAYS,
                DeliveryReminderClassifier.classify(TODAY.plusDays(3), TODAY));
    }

    @Test
    void oneDayAway() {
        assertEquals(DeliveryReminderClassifier.Reminder.ONE_DAY,
                DeliveryReminderClassifier.classify(TODAY.plusDays(1), TODAY));
    }

    @Test
    void dueToday() {
        assertEquals(DeliveryReminderClassifier.Reminder.DUE_TODAY,
                DeliveryReminderClassifier.classify(TODAY, TODAY));
    }

    @Test
    void overdue() {
        assertEquals(DeliveryReminderClassifier.Reminder.OVERDUE,
                DeliveryReminderClassifier.classify(TODAY.minusDays(1), TODAY));
        assertEquals(DeliveryReminderClassifier.Reminder.OVERDUE,
                DeliveryReminderClassifier.classify(TODAY.minusDays(30), TODAY));
    }

    @Test
    void noReminderForOtherOffsets() {
        assertEquals(DeliveryReminderClassifier.Reminder.NONE,
                DeliveryReminderClassifier.classify(TODAY.plusDays(2), TODAY));
        assertEquals(DeliveryReminderClassifier.Reminder.NONE,
                DeliveryReminderClassifier.classify(TODAY.plusDays(4), TODAY));
        assertEquals(DeliveryReminderClassifier.Reminder.NONE,
                DeliveryReminderClassifier.classify(TODAY.plusDays(30), TODAY));
    }

    @Test
    void nullDatesReturnNone() {
        assertEquals(DeliveryReminderClassifier.Reminder.NONE,
                DeliveryReminderClassifier.classify(null, TODAY));
        assertEquals(DeliveryReminderClassifier.Reminder.NONE,
                DeliveryReminderClassifier.classify(TODAY, null));
    }
}
