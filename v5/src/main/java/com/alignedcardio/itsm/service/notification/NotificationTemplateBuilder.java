package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds per-channel notification content from a single event payload.
 *
 * Two entry points:
 *  - forEvent(type, payload): hardcoded sends (IncidentService, SlaBreachMonitorJob, ...)
 *  - fromRule(action, event): automation-rule SEND_NOTIFICATION actions. Templates
 *    subject/body/pushTitle/pushBody from the action config; when the optional
 *    pushTitle/pushBody keys are absent, push content is auto-derived from the
 *    templated subject/body so existing rules upgrade with zero edits.
 */
@Component
public class NotificationTemplateBuilder {

    private static final int PUSH_TITLE_MAX = 45;
    private static final int PUSH_BODY_MAX = 120;

    private final String appBaseUrl;

    public NotificationTemplateBuilder(@Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    // ---- Automation-rule path ----

    public NotificationContent fromRule(JsonNode action, DomainEvent event) {
        String subject = template(action.get("subject").asText(), event);
        String body = template(action.get("body").asText(), event);
        String pushTitle = action.hasNonNull("pushTitle")
                ? template(action.get("pushTitle").asText(), event)
                : derivePushTitle(subject);
        String pushBody = action.hasNonNull("pushBody")
                ? template(action.get("pushBody").asText(), event)
                : derivePushBody(body);
        return new NotificationContent(subject, body, subject, body, pushTitle, pushBody);
    }

    // ---- Hardcoded event path ----

    public NotificationContent forEvent(String type, Map<String, ?> p) {
        return switch (type) {
            case "INCIDENT_ASSIGNED" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Assigned to you",
                    str(p, "actorName") + " assigned incident " + str(p, "number")
                            + " (\"" + str(p, "title") + "\") to you." + link(p),
                    str(p, "number") + " assigned to you by " + str(p, "actorName"),
                    "\"" + str(p, "title") + "\"",
                    str(p, "number") + " – Assigned",
                    str(p, "actorName") + " assigned this to you");
            case "INCIDENT_PRIORITY_CHANGED" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Priority escalated",
                    str(p, "actorName") + " changed priority of " + str(p, "number")
                            + " (\"" + str(p, "title") + "\") from " + str(p, "oldPriority")
                            + " to " + str(p, "newPriority") + ". Reason: " + str(p, "reason") + "." + link(p),
                    str(p, "number") + " priority: " + str(p, "oldPriority") + " → " + str(p, "newPriority"),
                    "Reason: " + str(p, "reason"),
                    str(p, "number") + " – Priority",
                    str(p, "oldPriority") + " → " + str(p, "newPriority"));
            case "INCIDENT_UPDATE" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Status: " + str(p, "newStatus"),
                    "Status of " + str(p, "number") + " (\"" + str(p, "title") + "\") changed from "
                            + str(p, "oldStatus") + " to " + str(p, "newStatus")
                            + " by " + str(p, "actorName") + "." + link(p),
                    str(p, "number") + " → " + str(p, "newStatus"),
                    "was " + str(p, "oldStatus"),
                    str(p, "number") + " – Status",
                    str(p, "oldStatus") + " → " + str(p, "newStatus"));
            case "INCIDENT_COMMENT" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – New comment",
                    str(p, "authorName") + " commented on " + str(p, "number")
                            + " (\"" + str(p, "title") + "\"): \"" + str(p, "commentPreview") + "\"" + link(p),
                    "New comment on " + str(p, "number") + " by " + str(p, "authorName"),
                    "\"" + str(p, "commentPreview") + "\"",
                    str(p, "number") + " – Comment",
                    str(p, "authorName") + ": " + str(p, "commentPreview"));
            case "MENTION" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – You were mentioned",
                    str(p, "authorName") + " mentioned you in a comment on " + str(p, "number")
                            + " (\"" + str(p, "title") + "\"): \"" + str(p, "commentPreview") + "\"" + link(p),
                    "You were mentioned in " + str(p, "number"),
                    str(p, "authorName") + ": \"" + str(p, "commentPreview") + "\"",
                    str(p, "number") + " – Mention",
                    str(p, "authorName") + " mentioned you");
            case "SLA_BREACH" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – SLA breached",
                    "The resolution SLA for incident " + str(p, "number")
                            + " (\"" + str(p, "title") + "\") has been breached." + link(p),
                    str(p, "number") + " SLA breached",
                    "\"" + str(p, "title") + "\"",
                    str(p, "number") + " – SLA breach",
                    "Resolution target missed");
            case "SLA_ESCALATION" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Escalated to tier " + str(p, "tierLevel"),
                    "Incident " + str(p, "number") + " (\"" + str(p, "title")
                            + "\") triggered escalation tier " + str(p, "tierLevel")
                            + " (" + str(p, "triggerType") + ")." + link(p),
                    str(p, "number") + " escalated to tier " + str(p, "tierLevel"),
                    str(p, "triggerType"),
                    str(p, "number") + " – Escalated",
                    "Tier " + str(p, "tierLevel") + " escalation");
            case "SLA_ESCALATION_ADMIN" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Auto-escalated to tier " + str(p, "tierLevel") + " — review needed",
                    "Incident " + str(p, "number") + " (\"" + str(p, "title")
                            + "\") was auto-escalated to tier " + str(p, "tierLevel")
                            + " (" + str(p, "triggerType") + "). Please review and assign." + link(p),
                    str(p, "number") + " auto-escalated to tier " + str(p, "tierLevel"),
                    str(p, "triggerType") + " — needs review/assignment",
                    str(p, "number") + " – Auto-escalated",
                    "Tier " + str(p, "tierLevel") + " — needs review");
            case "INCIDENT_CREATED_UNASSIGNED" -> content(
                    str(p, "number") + " – " + str(p, "title") + " – Needs assignment",
                    "Incident " + str(p, "number") + " (\"" + str(p, "title")
                            + "\") was created by " + str(p, "requesterName")
                            + " and has no assignee. Please assign it." + link(p),
                    str(p, "number") + " needs assignment",
                    "\"" + str(p, "title") + "\"",
                    str(p, "number") + " – Needs assignment",
                    "Unassigned incident — please assign");
            case "SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT" -> content(
                    str(p, "number") + " – " + str(p, "catalogItemName") + " – Needs fulfiller assignment",
                    "Service request " + str(p, "number") + " (\"" + str(p, "catalogItemName")
                            + "\") was approved and is ready for fulfillment. Please assign a fulfiller." + link(p),
                    str(p, "number") + " needs a fulfiller",
                    "\"" + str(p, "catalogItemName") + "\"",
                    str(p, "number") + " – Needs fulfiller",
                    "Approved request — assign a fulfiller");
            case "SERVICE_REQUEST_REMINDER" -> content(
                    str(p, "number") + " – " + str(p, "catalogItemName") + " – Approval reminder",
                    str(p, "requesterName") + " sent a reminder for " + str(p, "number")
                            + " (\"" + str(p, "catalogItemName") + "\"): \"" + str(p, "message") + "\"" + link(p),
                    str(p, "number") + " approval reminder",
                    "\"" + str(p, "message") + "\"",
                    str(p, "number") + " – Reminder",
                    "Reminder: please review " + str(p, "number"));
            default -> content(
                    str(p, "subject"), str(p, "body"),
                    str(p, "subject"), str(p, "body"),
                    derivePushTitle(str(p, "subject")), derivePushBody(str(p, "body")));
        };
    }

    // ---- Derivation helpers (also used for rules without push overrides) ----

    static String derivePushTitle(String subject) {
        return truncateAtWord(subject, PUSH_TITLE_MAX);
    }

    static String derivePushBody(String body) {
        if (body == null) return "";
        int end = body.indexOf(". ");
        String first = end > 0 ? body.substring(0, end + 1) : body;
        return truncateAtWord(first, PUSH_BODY_MAX);
    }

    private static String truncateAtWord(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        String cut = s.substring(0, max);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > max / 2) cut = cut.substring(0, lastSpace);
        return cut.trim() + "…";
    }

    private String link(Map<String, ?> p) {
        Object entityType = p.get("entityType");
        Object entityId = p.get("entityId");
        if (entityType == null || entityId == null) return "";
        return " View: " + appBaseUrl + "/" + String.valueOf(entityType).toLowerCase()
                + "s/" + entityId;
    }

    private NotificationContent content(String emailSubject, String emailBody,
                                        String inAppSubject, String inAppBody,
                                        String pushTitle, String pushBody) {
        return new NotificationContent(emailSubject, emailBody, inAppSubject, inAppBody,
                truncateAtWord(pushTitle, PUSH_TITLE_MAX), truncateAtWord(pushBody, PUSH_BODY_MAX));
    }

    private String template(String text, DomainEvent event) {
        String result = text;
        for (Map.Entry<String, Object> entry : event.payload().entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return result;
    }

    private static String str(Map<String, ?> p, String key) {
        Object v = p.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
