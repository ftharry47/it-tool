package com.alignedcardio.itsm.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator(new ObjectMapper());

    @Test
    void matchingConditionPasses() {
        String conditions = "[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"NEW\"}]";
        assertTrue(evaluator.evaluate(conditions, Map.of("status", "NEW", "impact", 1)));
    }

    @Test
    void nonMatchingConditionFails() {
        String conditions = "[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"CLOSED\"}]";
        assertFalse(evaluator.evaluate(conditions, Map.of("status", "NEW")));
    }

    @Test
    void inOperatorMatches() {
        String conditions = "[{\"field\":\"priority\",\"op\":\"in\",\"values\":[\"Critical\",\"High\"]}]";
        assertTrue(evaluator.evaluate(conditions, Map.of("priority", "High")));
    }

    @Test
    void numericComparisonMatches() {
        String conditions = "[{\"field\":\"impact\",\"op\":\"gte\",\"value\":2}]";
        assertTrue(evaluator.evaluate(conditions, Map.of("impact", 3)));
    }

    @Test
    void emptyConditionsAlwaysPass() {
        assertTrue(evaluator.evaluate("", Map.of("status", "NEW")));
        assertTrue(evaluator.evaluate("{}", Map.of("status", "NEW")));
    }
}
