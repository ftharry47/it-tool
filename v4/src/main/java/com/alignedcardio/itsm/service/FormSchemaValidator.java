package com.alignedcardio.itsm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class FormSchemaValidator {Two issues with notifications - investigate delivery first, then
redesign content across ALL THREE channels (in-app bell, browser push,
email) consistently.

============================================
PART 1 — Real-time delivery investigation
============================================
Notifications (in-app bell + email + browser push) are "not pushing
properly in real time" - trace the actual mechanics for each channel,
don't assume:

1. In-app (bell): confirm how NotificationBell gets new notifications
   - polling interval (what's the actual value - too slow?), or
   WebSocket/SSE? Report the real number.

2. Browser push (Stage E): confirm push notifications are actually
   firing immediately when a triggering event happens, not queued
   or delayed by anything in the pipeline - trace from
   NotificationService.send() through to PushService and confirm
   there's no batching/delay introduced anywhere in that path.

3. Email: check NotificationPreference.digestMode's default for new
   users - is email being queued into an hourly/daily digest by
   default (explaining "not real-time" - working as designed, just
   not immediate) rather than sending immediately for urgent event
   types (assignment, escalation, rejection)? Propose which event
   types should ALWAYS bypass digest mode and send immediately
   regardless of the user's digest preference (e.g., "assigned to
   you" and "SLA breached" probably shouldn't wait for a digest even
   if the user has digest mode on - a daily summary type notification
   legitimately can wait).

Report back definitively for all three channels: what's actually
happening right now (real poll intervals, actual digest default,
actual push latency), and whether each is (a) too slow but working
as designed, (b) genuinely broken/delayed somewhere in the pipeline,
or (c) fine and just needs a default tuned. Show me the evidence
(actual code/config values), don't guess.

============================================
PART 2 — Notification content redesign, per channel
============================================
Current notification content is a single terse sentence, sent
identically across all channels. Real ITSM tools tailor structure
and length PER CHANNEL:

- Email: full structure - Subject: "[Ticket#] [Short title] -
  [Action]" (e.g. "INC0010023 - Network outage - Assigned to you").
  Body: who did what, when, relevant context (comment text,
  rejection reason, new status), direct link to view the item.
- In-app bell: similar structure to email but more compact (list-
  view friendly) - ticket# + short action + timestamp, expandable or
  linking to detail.
- Browser push (OS notification): shortest version - push
  notifications have real length limits and get truncated by the OS
  (typically ~40-50 chars title, ~120-150 chars body) - title should
  be "[Ticket#] - [Action]" and body a single short context line,
  NOT the full email body truncated awkwardly. Clicking still deep-
  links to the item (already built in Stage E).

Audit EVERY notification-triggering event across the whole project -
check actual automation_rule seed data AND any hardcoded
NotificationService calls, don't rely on this list being complete:
- Incident: created, assigned/reassigned, status changed, escalated
  (priority change), commented (public), SLA breach/at-risk
- Service Request: pending approval, approved, rejected (with
  reason), moved to fulfillment, delivery date set, delivery
  reminders (3-day/1-day/due-today/overdue)
- Location: approval manager assigned, approval manager removed
- Any others you find

For each event type, show me a table: OLD content (single example),
NEW email subject+body, NEW in-app bell text, NEW push title+body -
side by side, for review before implementing any of them.

Propose a shared template-building approach (e.g., a
NotificationTemplateBuilder that takes the event data once and
produces all three channel-specific outputs from it) rather than
three separately hand-written strings per event type - this keeps
them consistent and avoids drift when a new notification type gets
added later.

============================================
WHEN DONE
============================================
1. Show me Part 1's findings (all three channels) and Part 2's full
   template table FIRST - don't implement until I've reviewed both.
2. Once approved, implement, run the full test suite, confirm 0
   failures, and show me before/after examples (all three channel
   versions) for at least 3-4 representative event types.
3. Rebuild via .\package.ps1 with the real Azure values (I'll run
   that myself) and give me redeploy steps.

After deployment, I'll test: trigger an assignment, a status change,
and a rejection with reason - checking all three channels arrive with
the new content and reasonable speed.

    static final int OTHER_OPTION_MAX_LENGTH = 255;

    private final ObjectMapper objectMapper;

    public FormSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(String schemaJson, String dataJson) {
        List<String> errors = new ArrayList<>();

        try {
            ArrayNode schema = (ArrayNode) objectMapper.readTree(schemaJson);
            ObjectNode data = dataJson == null || dataJson.isBlank()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) objectMapper.readTree(dataJson);

            for (JsonNode field : schema) {
                String name = field.get("name").asText();
                String label = field.hasNonNull("label") ? field.get("label").asText() : name;
                String type = field.hasNonNull("type") ? field.get("type").asText() : "string";
                boolean required = field.hasNonNull("required") && field.get("required").asBoolean();

                JsonNode value = data.get(name);

                if (required && (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank()))) {
                    errors.add(label + " is required");
                    continue;
                }

                if (value == null || value.isNull()) {
                    continue;
                }

                if ("string".equals(type) && !value.isTextual()) {
                    errors.add(label + " must be a string");
                } else if ("number".equals(type) && !value.isNumber()) {
                    errors.add(label + " must be a number");
                } else if ("boolean".equals(type) && !value.isBoolean()) {
                    errors.add(label + " must be a boolean");
                } else if ("select".equals(type) && value.isTextual() && field.hasNonNull("options")) {
                    String selected = value.asText();
                    if (!matchesOption(field.get("options"), selected)) {
                        errors.add(label + " has an invalid option");
                    }
                } else if ("select_with_other".equals(type)) {
                    if (!value.isTextual()) {
                        errors.add(label + " must be a string");
                    } else {
                        String selected = value.asText();
                        boolean isPreset = field.hasNonNull("options")
                                && matchesOption(field.get("options"), selected);
                        if (!isPreset && selected.length() > OTHER_OPTION_MAX_LENGTH) {
                            errors.add(label + " must be " + OTHER_OPTION_MAX_LENGTH + " characters or fewer");
                        }
                    }
                }
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid schema or form data JSON", e);
        } catch (ClassCastException e) {
            throw new IllegalStateException("Schema must be a JSON array of field definitions", e);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Form validation failed: " + String.join("; ", errors));
        }
    }

    private boolean matchesOption(JsonNode options, String selected) {
        for (JsonNode option : options) {
            if (option.isTextual() && option.asText().equals(selected)) {
                return true;
            }
            if (option.isObject() && option.hasNonNull("value") && option.get("value").asText().equals(selected)) {
                return true;
            }
        }
        return false;
    }
}
