package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.IssueService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class FieldUpdateHandler {

    private final IncidentService incidentService;
    private final IssueService issueService;

    public FieldUpdateHandler(IncidentService incidentService, IssueService issueService) {
        this.incidentService = incidentService;
        this.issueService = issueService;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String entity = action.hasNonNull("entity") ? action.get("entity").asText() : event.triggerEntity();
        String field = action.get("field").asText();
        String value = action.get("value").asText();
        UUID orgId = event.orgId();
        UUID entityId = event.entityId();

        if ("status".equalsIgnoreCase(field) || "workflowStatus".equalsIgnoreCase(field)) {
            throw new IllegalStateException(
                    "SET_FIELD does not support status; use SET_STATUS to enforce the state machine");
        }

        if ("INCIDENT".equals(entity)) {
            incidentService.updateField(orgId, actingUserId, entityId, field, value);
        } else if ("ISSUE".equals(entity)) {
            issueService.updateField(orgId, actingUserId, entityId, field, value);
        } else {
            throw new IllegalStateException("Unsupported entity for SET_FIELD: " + entity);
        }
    }
}
