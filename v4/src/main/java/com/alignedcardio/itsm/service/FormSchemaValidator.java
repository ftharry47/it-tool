package com.alignedcardio.itsm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FormSchemaValidator {

    private final ObjectMapper objectMapper;

    public FormSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String schemaJson, String dataJson) {
        List<String> errors = new ArrayList<>();

        try {
            ArrayNode schema = (ArrayNode) objectMapper.readTree(schemaJson);
            ObjectNode data = dataJson == null || dataJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(dataJson);

            for (JsonNode field : schema) {
                String name = field.get("name").asText();
                String label = field.hasNonNull("label") ? field.get("label").asText() : name;
                String type = field.hasNonNull("type") ? field.get("type").asText() : "string";
                boolean required = field.hasNonNull("required") && field.get("required").asBoolean();

                JsonNode value = data.get(name);

                if (required && (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank()))) {
                    errors.add(label + " is required");
                    continue;
                }

                if (value == null || value.isNull()) {
                    continue;
                }

                if ("string".equals(type) && !value.isTextual()) {
                    errors.add(label + " must be a string");
                } else if ("number".equals(type) && !value.isNumber()) {
                    errors.add(label + " must be a number");
                } else if ("boolean".equals(type) && !value.isBoolean()) {
                    errors.add(label + " must be a boolean");
                } else if ("select".equals(type) && value.isTextual() && field.hasNonNull("options")) {
                    String selected = value.asText();
                    boolean valid = false;
                    for (JsonNode option : field.get("options")) {
                        if (option.isTextual() && option.asText().equals(selected)) {
                            valid = true;
                            break;
                        } else if (option.isObject() && option.hasNonNull("value") && option.get("value").asText().equals(selected)) {
                            valid = true;
                            break;
                        }
                    }
                    if (!valid) {
                        errors.add(label + " has an invalid option");
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid schema or form data JSON", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Schema must be a JSON array of field definitions", e);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Form validation failed: " + String.join("; ", errors));
        }
    }
}
