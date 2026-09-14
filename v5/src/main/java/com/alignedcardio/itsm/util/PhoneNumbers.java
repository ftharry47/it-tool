package com.alignedcardio.itsm.util;

import java.util.regex.Pattern;

/**
 * Shared phone validation: required US 10-digit number. Human-friendly
 * formatting characters (spaces, dashes, parentheses, dots, leading +)
 * are accepted but stripped; exactly 10 digits must remain. Storage is
 * normalized to the plain 10-digit form (see {@link #normalize}).
 */
public final class PhoneNumbers {

    private static final Pattern ALLOWED_CHARS = Pattern.compile("^[0-9\\s\\-().+]+$");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    public static final String ERROR_MESSAGE =
            "Please enter a valid 10-digit phone number";

    private PhoneNumbers() {
    }

    public static boolean isValid(String phone) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        String trimmed = phone.trim();
        if (!ALLOWED_CHARS.matcher(trimmed).matches()) {
            return false;
        }
        return NON_DIGITS.matcher(trimmed).replaceAll("").length() == 10;
    }

    public static void requireValid(String phone) {
        if (!isValid(phone)) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
    }

    /**
     * Validates, then returns the normalized storage form: the 10 digits only,
     * with all formatting characters removed (e.g. "(555) 123-4567" -> "5551234567").
     */
    public static String normalize(String phone) {
        requireValid(phone);
        return NON_DIGITS.matcher(phone.trim()).replaceAll("");
    }
}
