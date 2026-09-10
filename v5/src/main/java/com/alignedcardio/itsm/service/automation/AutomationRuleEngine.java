package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.entity.AutomationRunLog;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import com.alignedcardio.itsm.repository.AutomationRunLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class AutomationRuleEngine {

    private final AutomationRuleRepository ruleRepository;
    private final AutomationRunLogRepository runLogRepository;
    private final ConditionEvaluator conditionEvaluator;
    private final AutomationActionExecutor actionExecutor;
    private final ObjectMapper objectMapper;

    public AutomationRuleEngine(AutomationRuleRepository ruleRepository,
                                AutomationRunLogRepository runLogRepository,
                                ConditionEvaluator conditionEvaluator,
                                AutomationActionExecutor actionExecutor,
                                ObjectMapper objectMapper) {
        this.ruleRepository = ruleRepository;
        this.runLogRepository = runLogRepository;
        this.conditionEvaluator = conditionEvaluator;
        this.actionExecutor = actionExecutor;
        this.objectMapper = objectMapper;
    }

    @EventListener
    @Transactional
    public void onDomainEvent(DomainEvent event) {
        List<AutomationRule> rules = ruleRepository
                .findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(
                        event.orgId(), event.triggerEntity(), event.triggerType());

        String payloadJson = toJson(event.payload());
        String eventName = event.triggerEntity() + "." + event.triggerType();

        for (AutomationRule rule : rules) {
            boolean matched = conditionEvaluator.evaluate(rule.getConditions(), event.payload());
            UUID actor = rule.getUpdatedBy() != null ? rule.getUpdatedBy() : rule.getCreatedBy();
            logRun(rule, event, payloadJson, eventName, matched, actor);
        }
    }

    private void logRun(AutomationRule rule, DomainEvent event, String payloadJson, String eventName, boolean matched, UUID actor) {
        AutomationRunLog log = new AutomationRunLog();
        log.setOrgId(rule.getOrgId());
        log.setRule(rule);
        log.setEntityType(event.triggerEntity());
        log.setEntityId(event.entityId());
        log.setTriggeredEvent(eventName);
        log.setPayload(payloadJson);

        if (!matched) {
            log.setStatus("NO_MATCH");
            log.setOutput("Condition stub rejected");
        } else {
            try {
                String output = actionExecutor.execute(rule, event);
                log.setStatus("EXECUTED");
                log.setOutput(output);
            } catch (WebhookException e) {
                log.setStatus("FAILED");
                log.setOutput(e.getActionOutput());
                log.setError(e.getMessage());
            } catch (Exception e) {
                log.setStatus("ERROR");
                log.setOutput("Action execution failed");
                log.setError(e.getMessage());
            }
        }

        log.setExecutedAt(OffsetDateTime.now());
        runLogRepository.save(log);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
