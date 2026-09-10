package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.api.project.IssueStatusChangeRequest;
import com.alignedcardio.itsm.entity.Incident;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.IssueService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StatusChangeHandler {

    private final IncidentService incidentService;
    private final IssueService issueService;

    public StatusChangeHandler(IncidentService incidentService, IssueService issueService) {
        this.incidentService = incidentService;
        this.issueService = issueService;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String entity = action.hasNonNull("entity") ? action.get("entity").asText() : event.triggerEntity();
        UUID orgId = event.orgId();
        UUID entityId = event.entityId();

        if ("INCIDENT".equals(entity)) {
            Incident.Status newStatus = Incident.Status.valueOf(action.get("value").asText().toUpperCase());
            incidentService.updateStatus(actingUserId, orgId, entityId, newStatus);
        } else if ("ISSUE".equals(entity)) {
            UUID workflowStatusId = UUID.fromString(action.get("value").asText());
            issueService.changeStatus(orgId, actingUserId, entityId,
                    new IssueStatusChangeRequest(workflowStatusId));
        } else {
            throw new IllegalStateException("Unsupported entity for SET_STATUS: " + entity);
        }
    }
}
