package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.service.IncidentService;
import com.alignedcardio.itsm.service.IssueService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssignHandler {

    private final IncidentService incidentService;
    private final IssueService issueService;

    public AssignHandler(IncidentService incidentService, IssueService issueService) {
        this.incidentService = incidentService;
        this.issueService = issueService;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String type = action.get("type").asText();
        String entity = action.hasNonNull("entity") ? action.get("entity").asText() : event.triggerEntity();
        UUID targetId = UUID.fromString(action.get("targetId").asText());
        UUID orgId = event.orgId();
        UUID entityId = event.entityId();

        if ("ASSIGN_TO_USER".equals(type)) {
            if ("INCIDENT".equals(entity)) {
                incidentService.assign(actingUserId, orgId, entityId, targetId);
            } else if ("ISSUE".equals(entity)) {
                issueService.assign(orgId, actingUserId, entityId, targetId);
            } else {
                throw new IllegalStateException("Unsupported entity for ASSIGN_TO_USER: " + entity);
            }
        } else if ("ASSIGN_TO_TEAM".equals(type)) {
            if ("INCIDENT".equals(entity)) {
                incidentService.assignTeam(actingUserId, orgId, entityId, targetId);
            } else if ("ISSUE".equals(entity)) {
                issueService.assignTeam(orgId, actingUserId, entityId, targetId);
            } else {
                throw new IllegalStateException("Unsupported entity for ASSIGN_TO_TEAM: " + entity);
            }
        } else {
            throw new IllegalStateException("Unsupported assignment type: " + type);
        }
    }
}
