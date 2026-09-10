package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AppUser;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.repository.AppUserRepository;
import com.alignedcardio.itsm.service.IncidentCommentService;
import com.alignedcardio.itsm.service.IssueCommentService;
import com.alignedcardio.itsm.service.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AddCommentHandler {

    private final IncidentCommentService incidentCommentService;
    private final IssueCommentService issueCommentService;
    private final AppUserRepository appUserRepository;

    public AddCommentHandler(IncidentCommentService incidentCommentService,
                             IssueCommentService issueCommentService,
                             AppUserRepository appUserRepository) {
        this.incidentCommentService = incidentCommentService;
        this.issueCommentService = issueCommentService;
        this.appUserRepository = appUserRepository;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String entity = action.hasNonNull("entity") ? action.get("entity").asText() : event.triggerEntity();
        String body = action.get("body").asText("Automated action executed");
        UUID orgId = event.orgId();
        UUID entityId = event.entityId();

        if ("INCIDENT".equals(entity)) {
            AppUser author = appUserRepository.findById(actingUserId)
                    .orElseThrow(() -> new NotFoundException("Rule actor not found"));
            incidentCommentService.addAutomationComment(orgId, entityId, author, body);
        } else if ("ISSUE".equals(entity)) {
            issueCommentService.addAutomationComment(orgId, actingUserId, entityId, body);
        } else {
            throw new IllegalStateException("Unsupported entity for ADD_COMMENT: " + entity);
        }
    }
}
