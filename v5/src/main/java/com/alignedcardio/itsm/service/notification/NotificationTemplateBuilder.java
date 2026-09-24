package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import com.alignedcardio.itsm.util.DateFormats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    private record TemplateSpec(String subject, String body, String inAppBody) {
        TemplateSpec(String subject, String body) {
            this(subject, body, null);
        }
    }

    private static final Map<String, TemplateSpec> TEMPLATES = new HashMap<>();

    static {
        TEMPLATES.put("INCIDENT_ASSIGNED", new TemplateSpec(
                "{{number}} — {{title}} — Assigned to you",
                """
Hi {{assigneeFirstName}},

{{actorName}} has assigned the following incident to you:

Incident {{number}} — {{title}}
Priority: {{priority}}   Status: {{status}}   Location: {{location}}

Please review and begin work.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("INCIDENT_UPDATE", new TemplateSpec(
                "{{number}} — {{title}} — Status: {{newStatus}}",
                """
Hi {{recipientFirstName}},

The status of your incident has changed:

Incident {{number}} — {{title}}
Status: {{oldStatus}} → {{newStatus}}
Changed by: {{actorName}}

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("INCIDENT_TIER_ESCALATED", new TemplateSpec(
                "{{number}} — {{title}} — Escalated to {{newTierName}}",
                """
Hello,

The following incident has been escalated to your tier and needs attention:

Incident {{number}} — {{title}}
Escalated from: {{oldTierName}} → {{newTierName}}
Reason: {{reason}}
Escalated by: {{actorName}}

This ticket currently has no assignee — please assign someone from {{newTierName}}.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("SLA_BREACH", new TemplateSpec(
                "{{number}} — {{title}} — SLA breached",
                """
Hi {{requesterFirstName}},

We want to let you know the resolution target for your incident has been exceeded:

Incident {{number}} — {{title}}
Priority: {{priority}}   Expected resolution: {{targetTime}}
Actual: {{elapsedTime}} and counting

This has been automatically escalated for faster attention.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("SLA_AT_RISK", new TemplateSpec(
                "{{number}} — {{title}} — SLA at risk",
                """
Hi {{assigneeFirstName}},

The resolution target for the following incident is at 75% and approaching breach:

Incident {{number}} — {{title}}
Priority: {{priority}}   Expected resolution: {{targetTime}}
Elapsed: {{elapsedTime}}

Please prioritise this ticket.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("INCIDENT_CREATED_UNASSIGNED", new TemplateSpec(
                "{{number}} — {{title}} — Needs assignment",
                """
Hello,

A new incident has been submitted and has no assignee yet:

Incident {{number}} — {{title}}
Submitted by: {{requesterName}}   Priority: {{priority}}

Please assign this to a support tier.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("MENTION", new TemplateSpec(
                "{{number}} — {{title}} — You were mentioned",
                """
Hi {{recipientFirstName}},

{{authorName}} mentioned you in a comment on {{number}} — {{title}}:

"{{commentPreview}}"

View full ticket:
{{appBaseUrl}}{{entityPath}}
"""));

        TEMPLATES.put("SLA_ESCALATION", new TemplateSpec(
                "{{number}} — {{title}} — Escalated to tier {{tierLevel}}",
                """
Hi {{recipientFirstName}},

Incident {{number}} — {{title}} has triggered an escalation ({{triggerType}}) to tier {{tierLevel}}.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("SLA_ESCALATION_ADMIN", new TemplateSpec(
                "{{number}} — {{title}} — Auto-escalated to tier {{tierLevel}} — review needed",
                """
Hello,

Incident {{number}} — {{title}} was auto-escalated to tier {{tierLevel}} ({{triggerType}}). Please review and assign.

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("INCIDENT_PRIORITY_CHANGED", new TemplateSpec(
                "{{number}} — {{title}} — Priority: {{newPriority}}",
                """
Hi {{recipientFirstName}},

The priority of incident {{number}} — {{title}} has changed:

Priority: {{oldPriority}} → {{newPriority}}
Reason: {{reason}}
Changed by: {{actorName}}

View full ticket:
{{appBaseUrl}}/dashboard/incidents/{{entityId}}
"""));

        TEMPLATES.put("SR_SENT_TO_APPROVAL", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Approval needed",
                """
Hi {{approverFirstName}},

A new service request is waiting for your approval:

Request {{number}} — {{catalogItemName}}
Requested by: {{requesterName}}   Location: {{locationName}}
Reason: {{reason}}

Please review and approve or reject.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("PENDING_APPROVAL", TEMPLATES.get("SR_SENT_TO_APPROVAL"));

        TEMPLATES.put("APPROVED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Approved",
                """
Hi {{requesterFirstName}},

Good news — your service request has been approved:

Request {{number}} — {{catalogItemName}}
Approved by: {{approverName}}
Note: {{approvalComment}}

Your request is now moving to fulfillment. We'll notify you at each step.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("REJECTED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Rejected",
                """
Hi {{requesterFirstName}},

Your service request was not approved:

Request {{number}} — {{catalogItemName}}
Rejected by: {{approverName}}
Reason: {{rejectionReason}}

If you have questions about this decision, please contact your approval manager directly.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("SERVICE_REQUEST_REMINDER", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Approval reminder",
                """
Hi {{approverFirstName}},

This is a reminder that a service request is still waiting for your approval:

Request {{number}} — {{catalogItemName}}
Waiting since: {{submittedDate}}

Message from {{adminName}}:
"{{reminderMessage}}"

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Needs fulfiller assignment",
                """
Hello,

The following request has been approved and is ready for fulfillment:

Request {{number}} — {{catalogItemName}}
Approved: {{approvedDate}}

Please assign a fulfiller from the IT Fulfillment team.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("IN_FULFILLMENT", TEMPLATES.get("SERVICE_REQUEST_APPROVED_NEEDS_ASSIGNMENT"));

        TEMPLATES.put("FULFILLMENT_TASK_ASSIGNED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Assigned to you",
                """
Hi {{assigneeFirstName}},

You've been assigned to fulfill the following request:

Request {{number}} — {{catalogItemName}}
Task: {{taskDescription}}
Assigned by: {{actorName}}

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("TASK_ASSIGNED", TEMPLATES.get("FULFILLMENT_TASK_ASSIGNED"));

        TEMPLATES.put("DELIVERY_DATE_SET_REQUESTER", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Delivery date set",
                """
Hi {{requesterFirstName}},

A delivery date has been set for your request:

Request {{number}} — {{catalogItemName}}
Task: {{taskDescription}}
Expected delivery: {{expectedDeliveryDate}}

We'll remind you again as the date approaches.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("DELIVERY_DATE_SET_ASSIGNEE", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Install visit on {{expectedDeliveryDate}}",
                """
Hi {{assigneeFirstName}},

Task {{taskDescription}} on request {{number}} ({{catalogItemName}}) will be delivered to {{locationName}} on {{expectedDeliveryDate}} — please plan to visit and install.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("DELIVERY_DATE_SET_TEAM", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Delivery date set",
                """
Hi there,

A delivery date has been set for request {{number}}:

Request {{number}} — {{catalogItemName}}
Task: {{taskDescription}}
Expected delivery: {{expectedDeliveryDate}}

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("FULFILLMENT_REMINDER", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Delivery {{phase}}",
                """
Hi {{recipientFirstName}},

This is a reminder about your upcoming delivery:

Request {{number}} — {{catalogItemName}}
Task: {{taskDescription}}
Expected delivery: {{expectedDeliveryDate}} ({{phase}})

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("DELIVERED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Fulfilled",
                """
Hi {{requesterFirstName}},

Your service request has been completed:

Request {{number}} — {{catalogItemName}}
Status: Fulfilled
Completed by: {{fulfillerName}}

If anything isn't working as expected, please reply or raise a new incident.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("FULFILLED", TEMPLATES.get("DELIVERED"));

        TEMPLATES.put("PROBLEM_ASSIGNED", new TemplateSpec(
                "{{number}} — {{title}} — Assigned to you",
                """
Hi {{assigneeFirstName}},

You've been assigned to investigate the following {{entityType}}:

{{entityType}} {{number}} — {{title}}
Assigned by: {{actorName}}

View full record:
{{appBaseUrl}}/dashboard/{{entityTypePlural}}/{{entityId}}
"""));

        TEMPLATES.put("CHANGE_ASSIGNED", TEMPLATES.get("PROBLEM_ASSIGNED"));

        TEMPLATES.put("APPROVAL_MANAGER_ASSIGNED", new TemplateSpec(
                "You are now the approval manager for {{locationName}}",
                """
Hi {{managerFirstName}},

You have been assigned as the approval manager for:
Location: {{locationName}}
Service requests submitted for this location will now route to you for approval.

View your approvals queue:
{{appBaseUrl}}/dashboard/approvals
"""));

        TEMPLATES.put("APPROVAL_MANAGER_REMOVED", new TemplateSpec(
                "You are no longer the approval manager for {{locationName}}",
                """
Hi {{managerFirstName}},

You have been removed as the approval manager for location {{locationName}}.

View your approvals queue:
{{appBaseUrl}}/dashboard/approvals
"""));

        TEMPLATES.put("SERVICE_REQUEST_SUBMITTED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Request received",
                """
Hi there,

Your service request has been received and is now being processed:

Request {{number}} — {{catalogItemName}}
Location: {{locationName}}

We'll notify you at each step.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("SERVICE_REQUEST_CANCELLED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Cancelled",
                """
Hi {{recipientFirstName}},

The following service request has been cancelled by {{actorName}}:

Request {{number}} — {{catalogItemName}}
Status: Cancelled
Reason: {{reason}}

If you did not request this or have questions, please contact your IT team.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));

        TEMPLATES.put("ISSUE_CREATED", new TemplateSpec(
                "{{projectKey}}: {{key}} — Issue created",
                """
Hi {{recipientFirstName}},

A new issue has been created in {{projectKey}}:

Issue {{key}} — {{summary}}
Priority: {{priority}}

View issue:
{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{entityId}}
"""));

        TEMPLATES.put("ISSUE_STATUS_CHANGED", new TemplateSpec(
                "{{projectKey}}: {{key}} — Status: {{newStatus}}",
                """
Hi {{recipientFirstName}},

The status of issue {{key}} has changed:

Issue {{key}} — {{summary}}
Status: {{oldStatus}} → {{newStatus}}

View issue:
{{appBaseUrl}}/dashboard/projects/{{projectId}}/issues/{{entityId}}
"""));

        TEMPLATES.put("SR_RETROACTIVE_APPROVAL_REJECTED", new TemplateSpec(
                "{{number}} — {{catalogItemName}} — Needs further review",
                """
Hi {{requesterFirstName}},

Your service request {{number}} ({{catalogItemName}}) needs further review before it can be processed.

This is not a final rejection — the approval team will follow up with more information.

View full request:
{{appBaseUrl}}/dashboard/service-requests/{{entityId}}
"""));
    }

    public NotificationTemplateBuilder(@Value("${app.base-url:http://localhost:8080}") String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    // ---- Automation-rule path ----

    public NotificationContent fromRule(JsonNode action, DomainEvent event) {
        Map<String, Object> values = withBuiltIns(event.payload());
        String subject = EmailRenderer.fill(action.get("subject").asText(), values, appBaseUrl);
        String body = EmailRenderer.fill(action.get("body").asText(), values, appBaseUrl);
        String pushTitle = action.hasNonNull("pushTitle")
                ? EmailRenderer.fill(action.get("pushTitle").asText(), values, appBaseUrl)
                : derivePushTitle(subject);
        String pushBody = action.hasNonNull("pushBody")
                ? EmailRenderer.fill(action.get("pushBody").asText(), values, appBaseUrl)
                : derivePushBody(body);
        String html = EmailRenderer.fromPlainText(body);
        return new NotificationContent(subject, body, html, subject, inAppSummary(body),
                truncateAtWord(pushTitle, PUSH_TITLE_MAX), truncateAtWord(pushBody, PUSH_BODY_MAX));
    }

    // ---- Hardcoded event path ----

    public NotificationContent forEvent(String type, Map<String, ?> p) {
        Map<String, Object> values = withBuiltIns(p);
        if ("INCIDENT_COMMENT".equals(type) || "SR_COMMENT".equals(type)) {
            return commentContent(type, values);
        }
        TemplateSpec spec = TEMPLATES.get(type);
        if (spec == null) {
            String subject = str(values, "subject");
            String body = str(values, "body");
            return content(
                    subject, body, EmailRenderer.fromPlainText(body),
                    subject, body,
                    derivePushTitle(subject), derivePushBody(body));
        }
        return fromSpec(spec, values);
    }

    // ---- Builders ----

    private NotificationContent fromSpec(TemplateSpec spec, Map<String, Object> values) {
        String subject = EmailRenderer.fill(spec.subject, values, appBaseUrl);
        String body = EmailRenderer.fill(spec.body, values, appBaseUrl);
        String html = EmailRenderer.fromPlainText(body);
        String inApp = spec.inAppBody != null
                ? EmailRenderer.fill(spec.inAppBody, values, appBaseUrl)
                : inAppSummary(body);
        return content(subject, body, html, subject, inApp,
                derivePushTitle(subject), derivePushBody(body));
    }

    private NotificationContent commentContent(String type, Map<String, Object> values) {
        String number = str(values, "number");
        String title = str(values, "title");
        String author = str(values, "authorName");
        String recipientFirstName = str(values, "recipientFirstName");
        String entityUrl = appBaseUrl + "/dashboard/"
                + ("SR_COMMENT".equals(type) ? "service-requests" : "incidents") + "/" + str(values, "entityId");

        StringBuilder html = new StringBuilder();
        html.append("<p>").append(escape(recipientFirstName.isEmpty() ? "there" : recipientFirstName)).append(",</p>");
        html.append("<p>A new comment has been added to ")
                .append("SR_COMMENT".equals(type) ? "request" : "incident").append(" ")
                .append(escape(number)).append(" — ").append(escape(title))
                .append(" by ").append(escape(author)).append(".</p>");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> prior = (List<Map<String, Object>>) values.get("priorComments");
        @SuppressWarnings("unchecked")
        Map<String, Object> newest = (Map<String, Object>) values.get("newComment");

        if (prior != null && !prior.isEmpty()) {
            html.append("<p class=\"em-muted\" style=\"color:#6b7280; font-size:13px;\">Recent comments:</p>");
            List<Map<String, Object>> visible = prior.stream().filter(c -> Boolean.TRUE.equals(c.get("public"))).toList();
            int start = Math.max(0, visible.size() - 3);
            for (int i = start; i < visible.size(); i++) {
                appendComment(html, visible.get(i), true);
            }
        }
        if (newest != null) {
            appendComment(html, newest, false);
        } else {
            html.append("<p class=\"em-quote\" style=\"margin-top:12px; padding:12px; background:#fafafa; border-left:3px solid #dc2828;\">");
            html.append(escape(str(values, "commentPreview")));
            html.append("</p>");
        }

        html.append("<p>View full ").append("SR_COMMENT".equals(type) ? "request" : "ticket").append(":<br>");
        html.append("<a href=\"").append(escape(entityUrl)).append("\">").append(escape(entityUrl)).append("</a></p>");

        String subject = number + " — " + title + " — New comment";
        String plain = subject + ". " + author + " commented: " + str(values, "commentPreview");
        String fullHtml = EmailRenderer.render(html.toString());
        return content(subject, plain, fullHtml, subject, plain,
                derivePushTitle(subject), derivePushBody(plain));
    }

    private void appendComment(StringBuilder html, Map<String, Object> c, boolean grey) {
        String author = str(c, "authorName");
        String body = str(c, "body");
        // Timestamps render server-side in US Eastern — never the reader's
        // device timezone (emails have no access to it).
        String when = c.get("createdAt") instanceof OffsetDateTime t
                ? DateFormats.formatDateTime(t)
                : str(c, "createdAt");
        html.append("<div class=\"em-quote\" style=\"margin-bottom:12px; padding:12px; border-radius:4px; ");
        if (grey) {
            html.append("background:#fafafa; color:#374151;");
        } else {
            html.append("background:#fafafa; border-left:3px solid #dc2828;");
        }
        html.append("\">");
        html.append("<p class=\"em-muted\" style=\"margin:0; font-size:12px; color:#6b7280;\">").append(escape(author))
                .append(" · ").append(escape(when)).append("</p>");
        html.append("<p style=\"margin:4px 0 0 0;\">").append(EmailRenderer.escapeMultiline(body)).append("</p>");
        html.append("</div>");
    }

    private String escape(String s) {
        return EmailRenderer.escape(s);
    }

    // ---- Derivation helpers ----

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

    private NotificationContent content(String emailSubject, String emailBody,
                                        String emailHtmlBody, String inAppSubject,
                                        String inAppBody, String pushTitle,
                                        String pushBody) {
        return new NotificationContent(emailSubject, emailBody, emailHtmlBody,
                inAppSubject, inAppBody,
                truncateAtWord(pushTitle, PUSH_TITLE_MAX),
                truncateAtWord(pushBody, PUSH_BODY_MAX));
    }

    private Map<String, Object> withBuiltIns(Map<String, ?> p) {
        Map<String, Object> values = new HashMap<>();
        if (p != null) values.putAll(p);
        values.putIfAbsent("appBaseUrl", appBaseUrl);
        return values;
    }

    private String inAppSummary(String body) {
        if (body == null) return "";
        // Drop the greeting line and any link paragraph for the in-app preview.
        String[] paras = body.split("\\n\\n+");
        for (String para : paras) {
            String t = para.trim();
            if (t.startsWith("Hi ") || t.startsWith("Hello,") || t.startsWith("View ") || t.startsWith("http")) continue;
            if (!t.isEmpty()) {
                return truncateAtWord(t.replace("\n", " "), 160);
            }
        }
        return truncateAtWord(body, 160);
    }

    private static String str(Map<String, ?> p, String key) {
        Object v = p == null ? null : p.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
