package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.Incident;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusChangeHandlerTest {

    @Mock
    private IncidentService incidentService;

    @Mock
    private IssueService issueService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void illegalIncidentStatusTransitionPropagatesValidationFailure() {
        StatusChangeHandler handler = new StatusChangeHandler(incidentService, issueService);

        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();

        when(incidentService.updateStatus(any(UUID.class), eq(orgId), eq(incidentId), eq(Incident.Status.CLOSED)))
                .thenThrow(new IllegalStateException("Illegal status transition: NEW -> CLOSED"));

        ObjectNode action = objectMapper.createObjectNode();
        action.put("entity", "INCIDENT");
        action.put("value", "CLOSED");

        IncidentCreatedEvent event = new IncidentCreatedEvent(
                orgId, incidentId, Map.of("status", "NEW"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> handler.handle(event, action, UUID.randomUUID()));

        assertTrue(thrown.getMessage().contains("Illegal status transition"));
    }
}
