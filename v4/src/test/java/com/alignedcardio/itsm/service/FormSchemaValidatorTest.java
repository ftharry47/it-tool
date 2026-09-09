package com.alignedcardio.itsm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormSchemaValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FormSchemaValidator validator = new FormSchemaValidator(objectMapper);

    private static final String SCHEMA = """
            [
              {"name": "assetTag", "label": "Asset Tag", "type": "string", "required": true},
              {"name": "quantity", "label": "Quantity", "type": "number", "required": true},
              {"name": "urgent", "label": "Urgent", "type": "boolean"},
              {"name": "model", "label": "Model", "type": "select", "options": ["Standard", "Pro", "Enterprise"], "required": true}
            ]
            """;

    @Test
    void validSubmissionPasses() {
        String data = """
                {"assetTag": "ABC123", "quantity": 2, "urgent": false, "model": "Pro"}
                """;
        assertDoesNotThrow(() -> validator.validate(SCHEMA, data));
    }

    @Test
    void missingRequiredFieldIsRejected() {
        String data = """
                {"quantity": 2, "model": "Pro"}
                """;
        assertThrows(IllegalStateException.class,
                () -> validator.validate(SCHEMA, data),
                "Should reject missing assetTag");
    }

    @Test
    void wrongTypeIsRejected() {
        String data = """
                {"assetTag": "ABC123", "quantity": "two", "model": "Pro"}
                """;
        Exception e = assertThrows(IllegalStateException.class,
                () -> validator.validate(SCHEMA, data));
        assertTrue(e.getMessage().contains("must be a number"));
    }

    @Test
    void invalidSelectOptionIsRejected() {
        String data = """
                {"assetTag": "ABC123", "quantity": 1, "model": "Ultimate"}
                """;
        Exception e = assertThrows(IllegalStateException.class,
                () -> validator.validate(SCHEMA, data));
        assertTrue(e.getMessage().contains("invalid option"));
    }

    private static final String OTHER_SCHEMA = """
            [
              {"name": "need", "label": "What do you need", "type": "select_with_other",
               "options": ["Laptop issue", "Software install", "Access request"], "required": true}
            ]
            """;

    @Test
    void selectWithOtherAcceptsPresetOption() {
        assertDoesNotThrow(() -> validator.validate(OTHER_SCHEMA, "{\"need\": \"Laptop issue\"}"));
    }

    @Test
    void selectWithOtherAcceptsFreeText() {
        assertDoesNotThrow(() -> validator.validate(OTHER_SCHEMA, "{\"need\": \"Custom request not in list\"}"));
    }

    @Test
    void selectWithOtherRejectsBlankOnRequiredField() {
        Exception e = assertThrows(IllegalStateException.class,
                () -> validator.validate(OTHER_SCHEMA, "{\"need\": \"\"}"));
        assertTrue(e.getMessage().contains("is required"));
    }

    @Test
    void selectWithOtherRejectsMissingOnRequiredField() {
        assertThrows(IllegalStateException.class,
                () -> validator.validate(OTHER_SCHEMA, "{}"));
    }

    @Test
    void selectWithOtherRejectsOtherValueOver255Chars() {
        String longValue = "x".repeat(256);
        Exception e = assertThrows(IllegalStateException.class,
                () -> validator.validate(OTHER_SCHEMA, "{\"need\": \"" + longValue + "\"}"));
        assertTrue(e.getMessage().contains("255 characters or fewer"));
    }

    @Test
    void selectWithOtherAcceptsOtherValueAt255Chars() {
        String maxValue = "x".repeat(255);
        assertDoesNotThrow(() -> validator.validate(OTHER_SCHEMA, "{\"need\": \"" + maxValue + "\"}"));
    }

    @Test
    void selectWithOtherRejectsNonTextualValue() {
        Exception e = assertThrows(IllegalStateException.class,
                () -> validator.validate(OTHER_SCHEMA, "{\"need\": 42}"));
        assertTrue(e.getMessage().contains("must be a string"));
    }
}
