package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.api.automation.AutomationRuleTestRequest;
import com.alignedcardio.itsm.api.automation.AutomationRuleTestResponse;
import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Service
public class AutomationRuleTestService {

    private final AutomationRuleRepository ruleRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;

    public AutomationRuleTestService(AutomationRuleRepository ruleRepository,
                                     ConditionEvaluator conditionEvaluator,
                                     ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AutomationRuleTestResponse test(UUID orgId, UUID ruleId, AutomationRuleTestRequest request) {
        AutomationRule rule = ruleRepository.findByOrgIdAndIdAndDeletedAtIsNull(orgId, ruleId)
                .orElseThrow(() -> new com.alignedcardio.itsm.service.NotFoundException("Automation rule not found"));

        if (!rule.isActive()) {
            return new AutomationRuleTestResponse(false, objectMapper.createArrayNode());
        }

        boolean matched = conditionEvaluator.evaluate(rule.getConditions(), request.samplePayload());

        if (!matched) {
            return new AutomationRuleTestResponse(false, objectMapper.createArrayNode());
        }

        JsonNode actions;
        try {
            actions = objectMapper.readTree(rule.getActions());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid rule actions JSON", e);
        }

        if (!actions.isArray()) {
            throw new IllegalStateException("Rule actions must be a JSON array");
        }

        return new AutomationRuleTestResponse(true, actions);
    }
}
