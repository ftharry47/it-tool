package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.service.notification.NotificationHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutomationActionExecutorTest {

    @Mock
    private FieldUpdateHandler fieldUpdateHandler;

    @Mock
    private AddCommentHandler addCommentHandler;

    @Mock
    private StatusChangeHandler statusChangeHandler;

    @Mock
    private AssignHandler assignHandler;

    @Mock
    private WebhookHandler webhookHandler;

    @Mock
    private NotificationHandler notificationHandler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void webhookFailureContinuesOtherActionsAndThrowsWebhookException() {
        AutomationActionExecutor executor = new AutomationActionExecutor(
                objectMapper,
                fieldUpdateHandler,
                addCommentHandler,
                statusChangeHandler,
                assignHandler,
                webhookHandler,
                notificationHandler);
        UUID actor = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        AutomationRule rule = new AutomationRule();
        rule.setUpdatedBy(actor);
        rule.setActions("""
                [
                  {"type": "SET_FIELD", "field": "priorityId", "value": "11111111-1111-1111-1111-111111111111"},
                  {"type": "CALL_WEBHOOK", "url": "http://localhost/hook"},
                  {"type": "ADD_COMMENT", "body": "done"}
                ]
                """);

        doThrow(new WebhookException("blocked", "http://localhost/hook"))
                .when(webhookHandler).handle(any(), any(JsonNode.class), eq(actor));

        IncidentCreatedEvent event = new IncidentCreatedEvent(orgId, incidentId, Map.of("status", "NEW"));

        assertThrows(WebhookException.class, () -> executor.execute(rule, event));

        verify(fieldUpdateHandler).handle(eq(event), any(JsonNode.class), eq(actor));
        verify(webhookHandler).handle(eq(event), any(JsonNode.class), eq(actor));
        verify(addCommentHandler).handle(eq(event), any(JsonNode.class), eq(actor));
    }
}
