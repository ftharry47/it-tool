package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.IssueService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FieldUpdateHandlerTest {

    @Mock
    private IncidentService incidentService;

    @Mock
    private IssueService issueService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FieldUpdateHandler handler = new FieldUpdateHandler(incidentService, issueService);

    @Test
    void setFieldRejectsStatusToPreventStateMachineBypass() {
        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        ObjectNode action = objectMapper.createObjectNode();
        action.put("entity", "INCIDENT");
        action.put("field", "status");
        action.put("value", "CLOSED");

        IncidentCreatedEvent event = new IncidentCreatedEvent(
                orgId, incidentId, Map.of("status", "NEW"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> handler.handle(event, action, UUID.randomUUID()));

        assertEquals("SET_FIELD does not support status; use SET_STATUS to enforce the state machine",
                thrown.getMessage());
        verify(incidentService, never()).updateField(orgId, null, null, null, null);
    }
}
