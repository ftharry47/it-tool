package com.alignedcardio.itsm.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhoneNumbersTest {

    @Test
    void acceptsAndNormalizesCommonUsFormats() {
        String[] accepted = {
                "5551234567",
                "(555) 123-4567",
                "555-123-4567",
                "555.123.4567",
                "555 123 4567",
                "(555)123-4567"
        };
        for (String input : accepted) {
            assertTrue(PhoneNumbers.isValid(input), "should accept: " + input);
            assertEquals("5551234567", PhoneNumbers.normalize(input),
                    "should normalize: " + input);
        }
    }

    @Test
    void rejectsMissingBlankAndWrongDigitCounts() {
        assertFalse(PhoneNumbers.isValid(null));
        assertFalse(PhoneNumbers.isValid(""));
        assertFalse(PhoneNumbers.isValid("   "));
        // 9 digits
        assertFalse(PhoneNumbers.isValid("555123456"));
        // 11 digits (including the "+1" country-code form — spec is exactly 10)
        assertFalse(PhoneNumbers.isValid("55512345678"));
        assertFalse(PhoneNumbers.isValid("+1 555 123 4567"));
    }

    @Test
    void rejectsLettersAndDisallowedCharacters() {
        assertFalse(PhoneNumbers.isValid("55a1234567"));
        assertFalse(PhoneNumbers.isValid("call me at 5551234567"));
        assertFalse(PhoneNumbers.isValid("555-123-4567 ext 9"));
    }

    @Test
    void requireValidThrowsClearMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> PhoneNumbers.requireValid("555-1234"));
        assertEquals("Please enter a valid 10-digit phone number", ex.getMessage());

        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.requireValid(null));
        assertThrows(IllegalArgumentException.class, () -> PhoneNumbers.normalize("123"));
    }
}
