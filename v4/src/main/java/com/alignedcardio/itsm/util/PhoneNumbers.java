package com.alignedcardio.itsm.util;

import java.util.regex.Pattern;

/**
 * Shared phone validation: optional, but if present must contain 10-15 digits
 * (allowing +, spaces, dashes, parentheses as formatting characters).
 */
public final class PhoneNumbers {

    private static final Pattern ALLOWED_CHARS = Pattern.compile("^\\+?[0-9\\s\\-().]+$");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D");

    public static final String ERROR_MESSAGE =
            "Phone number must contain 10-15 digits and may only include digits, spaces, and + - ( ) .";

    private PhoneNumbers() {
    }

    public static boolean isValid(String phone) {
        if (phone == null || phone.isBlank()) {
            return true;
        }
        String trimmed = phone.trim();
        if (!ALLOWED_CHARS.matcher(trimmed).matches()) {
            return false;
        }
        int digits = NON_DIGITS.matcher(trimmed).replaceAll("").length();
        return digits >= 10 && digits <= 15;
    }

    public static void requireValid(String phone) {
        if (!isValid(phone)) {
            throw new IllegalArgumentException(ERROR_MESSAGE);
        }
    }
}
