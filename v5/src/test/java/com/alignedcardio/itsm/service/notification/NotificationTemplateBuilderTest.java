package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.event.IncidentAssignedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTemplateBuilderTest {

    private final NotificationTemplateBuilder builder =
            new NotificationTemplateBuilder("https://itsm.example.com");
    private final ObjectMapper om = new ObjectMapper();

    private Map<String, Object> incidentPayload() {
        return Map.of(
                "number", "INC0010023",
                "title", "Network outage",
                "actorName", "Jane Smith",
                "entityType", "INCIDENT",
                "entityId", UUID.randomUUID());
    }

    @Test
    void incidentAssignedProducesDistinctChannelContent() {
        NotificationContent c = builder.forEvent("INCIDENT_ASSIGNED", incidentPayload());

        assertEquals("INC0010023 — Network outage — Assigned to you", c.emailSubject());
        assertNotNull(c.emailHtmlBody());
        assertTrue(c.emailBody().contains("Jane Smith has assigned the following incident to you"));
        assertTrue(c.emailBody().contains("https://itsm.example.com/dashboard/incidents/"));
        assertEquals("INC0010023 — Network outage — Assigned to you", c.inAppSubject());
        assertTrue(c.pushTitle().length() <= 45);
        assertTrue(c.pushBody().length() <= 120);
    }

    @Test
    void statusChangeShowsTransition() {
        Map<String, Object> p = new java.util.HashMap<>(incidentPayload());
        p.put("oldStatus", "NEW");
        p.put("newStatus", "IN_PROGRESS");

        NotificationContent c = builder.forEvent("INCIDENT_UPDATE", p);

        assertTrue(c.emailSubject().contains("Status: IN_PROGRESS"));
        assertTrue(c.emailBody().contains("NEW → IN_PROGRESS"));
    }

    @Test
    void slaEscalationIncludesTierAndTrigger() {
        Map<String, Object> p = new java.util.HashMap<>(incidentPayload());
        p.put("tierLevel", 2);
        p.put("triggerType", "BREACH");

        NotificationContent c = builder.forEvent("SLA_ESCALATION", p);

        assertTrue(c.emailSubject().contains("tier 2"));
        assertTrue(c.emailBody().contains("(BREACH)"));
        assertTrue(c.pushBody().toLowerCase().contains("tier 2"));
    }

    @Test
    void ruleWithoutPushFieldsDerivesThem() throws Exception {
        JsonNode action = om.readTree("""
                {"userId":"{{requesterId}}","subject":"Incident #{{number}} assigned to {{assigneeName}}",
                 "body":"Incident #{{number}} has been assigned to {{assigneeName}}. Please review.","channel":"BOTH"}
                """);
        UUID requesterId = UUID.randomUUID();
        var event = new IncidentAssignedEvent(UUID.randomUUID(), UUID.randomUUID(),
                Map.of("number", "INC0010023", "assigneeName", "Jane Smith", "requesterId", requesterId));

        NotificationContent c = builder.fromRule(action, event);

        assertEquals("Incident #INC0010023 assigned to Jane Smith", c.emailSubject());
        // pushTitle derived from subject (≤45 chars), pushBody = first sentence
        assertTrue(c.pushTitle().length() <= 45);
        assertEquals("Incident #INC0010023 has been assigned to Jane Smith.", c.pushBody());
    }

    @Test
    void ruleWithPushOverridesUsesThem() throws Exception {
        JsonNode action = om.readTree("""
                {"userId":"{{requesterId}}","subject":"Long subject {{number}}","body":"Body {{number}}.",
                 "pushTitle":"{{number}} – Short","pushBody":"Tiny","channel":"BOTH"}
                """);
        var event = new IncidentAssignedEvent(UUID.randomUUID(), UUID.randomUUID(),
                Map.of("number", "INC0010023", "requesterId", UUID.randomUUID()));

        NotificationContent c = builder.fromRule(action, event);

        assertEquals("INC0010023 – Short", c.pushTitle());
        assertEquals("Tiny", c.pushBody());
    }

    @Test
    void longSubjectsTruncateAtWordBoundary() {
        String longSubject = "INC0010023 – A very long incident title that goes on and on – Assigned to you";
        String derived = NotificationTemplateBuilder.derivePushTitle(longSubject);
        assertTrue(derived.length() <= 46); // 45 + ellipsis
        assertTrue(derived.endsWith("…"));
        assertFalse(derived.substring(0, derived.length() - 1).endsWith(" ")); // no dangling partial word
    }
}
