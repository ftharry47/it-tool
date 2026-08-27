package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.entity.AutomationRunLog;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.event.SlaBreachEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.service.notification.NotificationHandler;
import com.alignedcardio.itsm.repository.AutomationRuleRepository;
import com.alignedcardio.itsm.repository.AutomationRunLogRepository;
import com.alignedcardio.itsm.service.IncidentCommentService;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.IssueCommentService;
import com.alignedcardio.itsm.service.IssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationRuleEngineEndToEndTest {

    @Mock
    private AutomationRuleRepository ruleRepository;

    @Mock
    private AutomationRunLogRepository runLogRepository;

    @Mock
    private IncidentService incidentService;

    @Mock
    private IssueService issueService;

    @Mock
    private IncidentCommentService incidentCommentService;

    @Mock
    private IssueCommentService issueCommentService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private NotificationHandler notificationHandler;

    private AutomationRuleEngine ruleEngine;
    private ObjectMapper objectMapper;
    private UUID orgId;
    private UUID entityId;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ConditionEvaluator conditionEvaluator = new ConditionEvaluator(objectMapper);
        WebhookUrlValidator urlValidator = new WebhookUrlValidator();
        WebhookHandler webhookHandler = new WebhookHandler(urlValidator);
        FieldUpdateHandler fieldUpdateHandler = new FieldUpdateHandler(incidentService, issueService);
        AddCommentHandler addCommentHandler = new AddCommentHandler(incidentCommentService, issueCommentService, appUserRepository);
        StatusChangeHandler statusChangeHandler = new StatusChangeHandler(incidentService, issueService);
        AssignHandler assignHandler = new AssignHandler(incidentService, issueService);

        AutomationActionExecutor actionExecutor = new AutomationActionExecutor(
                objectMapper, fieldUpdateHandler, addCommentHandler, statusChangeHandler, assignHandler, webhookHandler, notificationHandler);

        ruleEngine = new AutomationRuleEngine(ruleRepository, runLogRepository, conditionEvaluator, actionExecutor, objectMapper);

        orgId = UUID.randomUUID();
        entityId = UUID.randomUUID();

        actor = new AppUser();
        actor.setId(UUID.randomUUID());
        lenient().when(appUserRepository.findById(actor.getId())).thenReturn(Optional.of(actor));
    }

    @Test
    void incidentCreationWithMatchingRuleExecutesMultipleActionsInOrderAndLogsOneRun() {
        AutomationRule rule = new AutomationRule();
        rule.setId(UUID.randomUUID());
        rule.setOrgId(orgId);
        rule.setTriggerEntity("INCIDENT");
        rule.setTriggerType("CREATED");
        rule.setActive(true);
        rule.setCreatedBy(actor.getId());
        rule.setUpdatedBy(actor.getId());
        rule.setConditions("[{\"field\":\"priority\",\"op\":\"eq\",\"value\":\"P1\"}]");
        rule.setActions("["
                + "{\"type\":\"SET_FIELD\",\"field\":\"impact\",\"value\":\"5\"},"
                + "{\"type\":\"ADD_COMMENT\",\"body\":\"automated comment\"},"
                + "{\"type\":\"SET_STATUS\",\"value\":\"IN_PROGRESS\"}"
                + "]");

        when(ruleRepository.findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(orgId, "INCIDENT", "CREATED"))
                .thenReturn(List.of(rule));

        DomainEvent event = new IncidentCreatedEvent(
                orgId,
                entityId,
                Map.of(
                        "id", entityId,
                        "priority", "P1",
                        "status", "NEW",
                        "category", "Software"));

        ruleEngine.onDomainEvent(event);

        ArgumentCaptor<AutomationRunLog> logCaptor = ArgumentCaptor.forClass(AutomationRunLog.class);
        verify(runLogRepository).save(logCaptor.capture());

        AutomationRunLog log = logCaptor.getValue();
        assertEquals("EXECUTED", log.getStatus(), "output=" + log.getOutput() + ", error=" + log.getError());
        assertEquals("INCIDENT.CREATED", log.getTriggeredEvent());
        assertEquals(entityId.toString(), log.getEntityId());

        String output = log.getOutput();
        assertTrue(output.contains("SET_FIELD: OK"), output);
        assertTrue(output.contains("ADD_COMMENT: OK"), output);
        assertTrue(output.contains("SET_STATUS: OK"), output);

        int setFieldIndex = output.indexOf("SET_FIELD: OK");
        int addCommentIndex = output.indexOf("ADD_COMMENT: OK");
        int setStatusIndex = output.indexOf("SET_STATUS: OK");
        assertTrue(setFieldIndex < addCommentIndex, "SET_FIELD should come before ADD_COMMENT");
        assertTrue(addCommentIndex < setStatusIndex, "ADD_COMMENT should come before SET_STATUS");

        verify(incidentService).updateField(orgId, rule.getCreatedBy(), entityId, "impact", "5");
        verify(incidentCommentService).addAutomationComment(eq(orgId), eq(entityId), any(AppUser.class), eq("automated comment"));
        verify(incidentService).updateStatus(rule.getCreatedBy(), orgId, entityId, Incident.Status.IN_PROGRESS);
    }

    @Test
    void failingWebhookActionLogsCorrectlyAndDoesNotBlockOtherActions() {
        AutomationRule rule = new AutomationRule();
        rule.setId(UUID.randomUUID());
        rule.setOrgId(orgId);
        rule.setTriggerEntity("INCIDENT");
        rule.setTriggerType("CREATED");
        rule.setActive(true);
        rule.setCreatedBy(actor.getId());
        rule.setUpdatedBy(actor.getId());
        rule.setConditions("[]");
        rule.setActions("["
                + "{\"type\":\"ADD_COMMENT\",\"body\":\"before\"},"
                + "{\"type\":\"CALL_WEBHOOK\",\"url\":\"http://localhost:1/bad\"},"
                + "{\"type\":\"SET_FIELD\",\"field\":\"urgency\",\"value\":\"2\"}"
                + "]");

        when(ruleRepository.findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(orgId, "INCIDENT", "CREATED"))
                .thenReturn(List.of(rule));

        DomainEvent event = new IncidentCreatedEvent(
                orgId,
                entityId,
                Map.of(
                        "id", entityId,
                        "priority", "P2",
                        "status", "NEW"));

        ruleEngine.onDomainEvent(event);

        ArgumentCaptor<AutomationRunLog> logCaptor = ArgumentCaptor.forClass(AutomationRunLog.class);
        verify(runLogRepository).save(logCaptor.capture());

        AutomationRunLog log = logCaptor.getValue();
        assertEquals("FAILED", log.getStatus());

        String output = log.getOutput();
        assertTrue(output.contains("ADD_COMMENT: OK"), output);
        assertTrue(output.contains("CALL_WEBHOOK: FAILED"), output);
        assertTrue(output.contains("SET_FIELD: OK"), output);

        assertNotNull(log.getError());
        assertTrue(log.getError().toLowerCase().contains("blocked"));

        verify(incidentCommentService).addAutomationComment(eq(orgId), eq(entityId), any(AppUser.class), eq("before"));
        verify(incidentService).updateField(orgId, rule.getCreatedBy(), entityId, "urgency", "2");
    }

    @Test
    void slaBreachRiskRuleFiresWhenSlaBreachEventPublished() {
        UUID slaInstanceId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setId(UUID.randomUUID());
        rule.setOrgId(orgId);
        rule.setTriggerEntity("SLA");
        rule.setTriggerType("SLA_BREACH_RISK");
        rule.setActive(true);
        rule.setCreatedBy(actor.getId());
        rule.setUpdatedBy(actor.getId());
        rule.setConditions("[]");
        rule.setActions("["
                + "{\"type\":\"SEND_NOTIFICATION\",\"userId\":\"" + targetUserId + "\",\"subject\":\"SLA at risk\",\"body\":\"Incident SLA is at risk\",\"channel\":\"IN_APP\"}"
                + "]");

        when(ruleRepository.findByOrgIdAndTriggerEntityAndTriggerTypeAndActiveTrueAndDeletedAtIsNull(orgId, "SLA", "SLA_BREACH_RISK"))
                .thenReturn(List.of(rule));

        DomainEvent event = new SlaBreachEvent(
                orgId,
                slaInstanceId,
                "ON_TRACK",
                "AT_RISK",
                entityId,
                123L);

        ruleEngine.onDomainEvent(event);

        ArgumentCaptor<AutomationRunLog> logCaptor = ArgumentCaptor.forClass(AutomationRunLog.class);
        verify(runLogRepository).save(logCaptor.capture());

        AutomationRunLog log = logCaptor.getValue();
        assertEquals("EXECUTED", log.getStatus());
        assertEquals("SLA.SLA_BREACH_RISK", log.getTriggeredEvent());
        assertTrue(log.getOutput().contains("SEND_NOTIFICATION: OK"), log.getOutput());

        verify(notificationHandler).handle(any(SlaBreachEvent.class), any(), eq(rule.getUpdatedBy()));
    }
}
