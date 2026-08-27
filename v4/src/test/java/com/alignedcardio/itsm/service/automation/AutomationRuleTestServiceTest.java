package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.api.automation.AutomationRuleTestRequest;
import com.alignedcardio.itsm.api.automation.AutomationRuleTestResponse;
import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRuleTestServiceTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator(objectMapper);

    @Test
    void dryRunReturnsActionsWhenConditionsMatch() {
        UUID orgId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setId(ruleId);
        rule.setOrgId(orgId);
        rule.setActive(true);
        rule.setConditions("[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"NEW\"}]");
        rule.setActions("[{\"type\":\"ADD_COMMENT\",\"body\":\"hello\"}]");

        when(ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)).thenReturn(Optional.of(rule));

        AutomationRuleTestService service = new AutomationRuleTestService(ruleRepository, conditionEvaluator, objectMapper);
        AutomationRuleTestRequest request = new AutomationRuleTestRequest(
                Map.of("status", "NEW"), UUID.randomUUID());

        AutomationRuleTestResponse response = service.test(orgId, ruleId, request);

        assertTrue(response.matched());
        JsonNode actions = response.actions();
        assertEquals(1, actions.size());
        assertEquals("ADD_COMMENT", actions.get(0).get("type").asText());
    }

    @Test
    void dryRunReturnsNoActionsWhenConditionsDoNotMatch() {
        UUID orgId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setId(ruleId);
        rule.setOrgId(orgId);
        rule.setActive(true);
        rule.setConditions("[{\"field\":\"status\",\"op\":\"eq\",\"value\":\"NEW\"}]");
        rule.setActions("[{\"type\":\"ADD_COMMENT\",\"body\":\"hello\"}]");

        when(ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)).thenReturn(Optional.of(rule));

        AutomationRuleTestService service = new AutomationRuleTestService(ruleRepository, conditionEvaluator, objectMapper);
        AutomationRuleTestRequest request = new AutomationRuleTestRequest(
                Map.of("status", "CLOSED"), UUID.randomUUID());

        AutomationRuleTestResponse response = service.test(orgId, ruleId, request);

        assertFalse(response.matched());
        assertEquals(0, response.actions().size());
    }

    @Test
    void dryRunReturnsNoActionsWhenRuleIsInactive() {
        UUID orgId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setId(ruleId);
        rule.setOrgId(orgId);
        rule.setActive(false);
        rule.setConditions("[]");
        rule.setActions("[{\"type\":\"ADD_COMMENT\"}]");

        when(ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)).thenReturn(Optional.of(rule));

        AutomationRuleTestService service = new AutomationRuleTestService(ruleRepository, conditionEvaluator, objectMapper);
        AutomationRuleTestRequest request = new AutomationRuleTestRequest(
                Map.of("status", "NEW"), UUID.randomUUID());

        AutomationRuleTestResponse response = service.test(orgId, ruleId, request);

        assertFalse(response.matched());
        assertEquals(0, response.actions().size());
    }
}
