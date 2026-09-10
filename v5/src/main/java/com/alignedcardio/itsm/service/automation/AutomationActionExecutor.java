package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.entity.AutomationRule;
import com.alignedcardio.itsm.event.DomainEvent;
import com.alignedcardio.itsm.service.notification.NotificationHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class AutomationActionExecutor {

    private final ObjectMapper objectMapper;
    private final FieldUpdateHandler fieldUpdateHandler;
    private final AddCommentHandler addCommentHandler;
    private final StatusChangeHandler statusChangeHandler;
    private final AssignHandler assignHandler;
    private final WebhookHandler webhookHandler;
    private final NotificationHandler notificationHandler;

    public AutomationActionExecutor(ObjectMapper objectMapper,
                                    FieldUpdateHandler fieldUpdateHandler,
                                    AddCommentHandler addCommentHandler,
                                    StatusChangeHandler statusChangeHandler,
                                    AssignHandler assignHandler,
                                    WebhookHandler webhookHandler,
                                    NotificationHandler notificationHandler) {
        this.objectMapper = objectMapper;
        this.fieldUpdateHandler = fieldUpdateHandler;
        this.addCommentHandler = addCommentHandler;
        this.statusChangeHandler = statusChangeHandler;
        this.assignHandler = assignHandler;
        this.webhookHandler = webhookHandler;
        this.notificationHandler = notificationHandler;
    }

    public String execute(AutomationRule rule, DomainEvent event) {
        UUID actor = rule.getUpdatedBy() != null ? rule.getUpdatedBy() : rule.getCreatedBy();

        JsonNode actions;
        try {
            actions = objectMapper.readTree(rule.getActions());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid actions JSON", e);
        }

        if (!actions.isArray()) {
            throw new IllegalStateException("Rule actions must be a JSON array");
        }

        List<String> outputs = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (JsonNode action : actions) {
            String type = action.hasNonNull("type") ? action.get("type").asText() : null;
            if (type == null) {
                throw new IllegalStateException("Action missing 'type'");
            }

            try {
                switch (type) {
                    case "SET_FIELD" -> fieldUpdateHandler.handle(event, action, actor);
                    case "ADD_COMMENT" -> addCommentHandler.handle(event, action, actor);
                    case "SET_STATUS" -> statusChangeHandler.handle(event, action, actor);
                    case "ASSIGN_TO_USER", "ASSIGN_TO_TEAM" -> assignHandler.handle(event, action, actor);
                    case "CALL_WEBHOOK" -> webhookHandler.handle(event, action, actor);
                    case "SEND_NOTIFICATION" -> notificationHandler.handle(event, action, actor);
                    default -> throw new IllegalStateException("Unsupported action type: " + type);
                }
                outputs.add(type + ": OK");
            } catch (WebhookException e) {
                outputs.add(type + ": FAILED");
                errors.add(e.getMessage());
            }
        }

        String joinedOutput = String.join("; ", outputs);

        if (!errors.isEmpty()) {
            throw new WebhookException(
                    String.join("; ", errors),
                    joinedOutput);
        }

        return joinedOutput;
    }
}
