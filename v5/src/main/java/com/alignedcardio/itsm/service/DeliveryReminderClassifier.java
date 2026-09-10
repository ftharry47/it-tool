package com.alignedcardio.itsm.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Pure date-math for fulfillment delivery reminders. Tested with fixed dates,
 * same style as BusinessHoursCalculatorTest.
 */
public final class DeliveryReminderClassifier {

    public enum Reminder { THREE_DAYS, ONE_DAY, DUE_TODAY, OVERDUE, NONE }

    private DeliveryReminderClassifier() {
    }

    public static Reminder classify(LocalDate expectedDeliveryDate, LocalDate today) {
        if (expectedDeliveryDate == null || today == null) {
            return Reminder.NONE;
        }
        long daysUntil = ChronoUnit.DAYS.between(today, expectedDeliveryDate);
        if (daysUntil == 3) {
            return Reminder.THREE_DAYS;
        }
        if (daysUntil == 1) {
            return Reminder.ONE_DAY;
        }
        if (daysUntil == 0) {
            return Reminder.DUE_TODAY;
        }
        if (daysUntil < 0) {
            return Reminder.OVERDUE;
        }
        return Reminder.NONE;
    }
}
