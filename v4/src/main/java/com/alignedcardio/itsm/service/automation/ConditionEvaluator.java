package com.alignedcardio.itsm.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ConditionEvaluator {

    private final ObjectMapper objectMapper;

    public ConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean evaluate(String conditionsJson, Object payload) {
        if (conditionsJson == null || conditionsJson.isBlank() || conditionsJson.equals("{}")) {
            return true;
        }

        JsonNode conditions;
        try {
            conditions = objectMapper.readTree(conditionsJson);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid conditions JSON", e);
        }

        JsonNode payloadNode = objectMapper.valueToTree(payload);

        if (conditions.isObject()) {
            return matches(conditions, payloadNode);
        }

        if (conditions.isArray()) {
            for (JsonNode condition : conditions) {
                if (!matches(condition, payloadNode)) {
                    return false;
                }
            }
            return true;
        }

        return true;
    }

    private boolean matches(JsonNode condition, JsonNode payload) {
        if (!condition.hasNonNull("field") || !condition.hasNonNull("op")) {
            return true;
        }

        String field = condition.get("field").asText();
        String op = condition.get("op").asText().toLowerCase();
        JsonNode valueNode = condition.get("value");
        JsonNode valuesNode = condition.get("values");
        JsonNode payloadValue = resolveField(payload, field);

        return switch (op) {
            case "eq" -> compare(payloadValue, valueNode) == 0;
            case "ne" -> compare(payloadValue, valueNode) != 0;
            case "in" -> valuesNode != null && valuesNode.isArray() && isIn(payloadValue, valuesNode);
            case "gt" -> compare(payloadValue, valueNode) > 0;
            case "gte" -> compare(payloadValue, valueNode) >= 0;
            case "lt" -> compare(payloadValue, valueNode) < 0;
            case "lte" -> compare(payloadValue, valueNode) <= 0;
            default -> throw new IllegalStateException("Unsupported condition op: " + op);
        };
    }

    private JsonNode resolveField(JsonNode payload, String field) {
        if (field == null || payload == null) {
            return null;
        }

        String[] parts = field.split("\\.");
        JsonNode current = payload;
        for (String part : parts) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private boolean isIn(JsonNode payloadValue, JsonNode valuesNode) {
        if (payloadValue == null) {
            return false;
        }
        String payloadText = payloadValue.isTextual() ? payloadValue.asText() : payloadValue.toString();
        for (JsonNode value : valuesNode) {
            if (value.isTextual() && payloadText.equals(value.asText())) {
                return true;
            }
            if (!value.isTextual() && compare(payloadValue, value) == 0) {
                return true;
            }
        }
        return false;
    }

    private int compare(JsonNode left, JsonNode right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;

        if (left.isNumber() && right.isNumber()) {
            return Double.compare(left.asDouble(), right.asDouble());
        }

        if (left.isNumber() && right.isTextual()) {
            try {
                return Double.compare(left.asDouble(), Double.parseDouble(right.asText()));
            } catch (NumberFormatException ignored) {
            }
        }

        if (left.isTextual() && right.isNumber()) {
            try {
                return Double.compare(Double.parseDouble(left.asText()), right.asDouble());
            } catch (NumberFormatException ignored) {
            }
        }

        return left.asText().compareTo(right.asText());
    }
}
