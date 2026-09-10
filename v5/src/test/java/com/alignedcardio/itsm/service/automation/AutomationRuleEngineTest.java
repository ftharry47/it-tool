package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.entity.AutomationRunLog;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import com.alignedcardio.itsm.repository.AutomationRunLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockito.Mockito;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutomationRuleEngineTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private AutomationRunLogRepository runLogRepository;

    @Mock
    private ConditionEvaluator conditionEvaluator;

    @Mock
    private AutomationActionExecutor actionExecutor;

    private ObjectMapper objectMapper;
    private AutomationRuleEngine engine;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        engine = new AutomationRuleEngine(ruleRepository, runLogRepository, conditionEvaluator, actionExecutor, objectMapper);
    }

    @Test
    void incidentCreatedEventProducesRunLog() {
        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setId(ruleId);
        rule.setOrgId(orgId);
        rule.setName("Auto-assign network incidents");
        rule.setTriggerType("CREATED");
        rule.setTriggerEntity("INCIDENT");
        rule.setConditions("{}");
        rule.setActions("[]");
        rule.setCreatedBy(UUID.randomUUID());
        rule.setUpdatedBy(UUID.randomUUID());
        rule.setActive(true);

        when(ruleRepository
                .findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(
                        orgId, "INCIDENT", "CREATED"))
                .thenReturn(List.of(rule));
        when(conditionEvaluator.evaluate(any(), any())).thenReturn(true);

        IncidentCreatedEvent event = new IncidentCreatedEvent(
                orgId, incidentId, Map.of("status", "NEW"));

        engine.onDomainEvent(event);

        ArgumentCaptor<AutomationRunLog> captor = ArgumentCaptor.forClass(AutomationRunLog.class);
        verify(runLogRepository).save(captor.capture());

        AutomationRunLog log = captor.getValue();
        assertEquals(rule, log.getRule());
        assertEquals(incidentId, log.getEntityId());
        assertEquals("INCIDENT.CREATED", log.getTriggeredEvent());
        assertEquals("EXECUTED", log.getStatus());
        Mockito.verify(actionExecutor).execute(Mockito.eq(rule), Mockito.any());
    }
}
