package com.alignedcardio.itsm.service.automation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookUrlValidatorTest {

    private final WebhookUrlValidator validator = new WebhookUrlValidator();

    @Test
    void localhostIsBlocked() {
        WebhookException thrown = assertThrows(WebhookException.class,
                () -> validator.validate("http://localhost:8080/hook"));
        assertTrue(thrown.getMessage().toLowerCase().contains("local"));
    }

    @Test
    void privateIpIsBlocked() {
        WebhookException thrown = assertThrows(WebhookException.class,
                () -> validator.validate("http://192.168.1.1/hook"));
        assertTrue(thrown.getMessage().contains("private"));
    }

    @Test
    void publicHttpIsAllowed() {
        // This will attempt DNS resolution to alignedcardio.com.
        // If no network is available, UnknownHostException is acceptable for the test.
        try {
            validator.validate("https://alignedcardio.com/hook");
        } catch (WebhookException e) {
            assertTrue(e.getMessage().contains("resolved") || e.getMessage().contains("Could not"));
        }
    }
}
