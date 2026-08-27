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
}
