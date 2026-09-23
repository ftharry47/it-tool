package com.alignedcardio.itsm.service.notification;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommentEmailPreviewTest {

    @Test
    void printCommentEmailHtml() throws Exception {
        NotificationTemplateBuilder builder = new NotificationTemplateBuilder("https://itsm.example.com");

        // 16:23 UTC = 12:23 PM Eastern (EDT). Fixed value so the assertion is
        // deterministic regardless of where the build machine runs.
        OffsetDateTime commentTime = OffsetDateTime.parse("2026-09-21T16:23:00Z");

        Map<String, Object> newComment = Map.of(
                "authorName", "Jane Smith",
                "body", "The new router is on order and will arrive tomorrow.",
                "createdAt", commentTime,
                "public", true);

        Map<String, Object> prior = Map.of(
                "authorName", "John Doe",
                "body", "Can we get an ETA on the replacement hardware?",
                "createdAt", commentTime.minusHours(2),
                "public", true);

        Map<String, Object> payload = Map.of(
                "number", "INC0010023",
                "title", "Network outage",
                "authorName", "Jane Smith",
                "recipientFirstName", "Alex",
                "entityType", "INCIDENT",
                "entityId", UUID.randomUUID(),
                "commentPreview", "The new router is on order...",
                "newComment", newComment,
                "priorComments", List.of(prior));

        NotificationContent content = builder.forEvent("INCIDENT_COMMENT", payload);
        String html = content.emailHtmlBody();
        System.out.println(html);

        // 1+2: branding — "Azentro" only, copyright footer instead of Regards.
        assertTrue(html.contains(">Azentro<"));
        assertFalse(html.contains("One Mail Delivery System"));
        assertFalse(html.contains("Regards,"));
        assertTrue(html.contains("© 2026 Azentro. All Rights Reserved."));
        assertTrue(html.contains("If you have questions about this ticket"));

        // 3: comment timestamp renders in US Eastern, server-side.
        assertTrue(html.contains("09/21/2026, 12:23 PM"), html);
        assertFalse(html.contains("16:23")); // no UTC leakage

        // 4: calm design — no banner fill; thin red underline + left-border
        // quote accent are the only red surfaces. Links stay brand red.
        assertFalse(html.contains("background: #dc2828"), html);      // no banner fill
        assertFalse(html.contains("#fef2f2"));                        // no pink quote tint
        assertTrue(html.contains("border-bottom:3px solid #dc2828")); // wordmark underline
        assertTrue(html.contains("border-left:3px solid #dc2828"));   // quote accent
        assertFalse(html.contains("#0f172a"));
        assertFalse(html.contains("#2563eb"));
        assertFalse(html.contains("#eff6ff"));

        // 5: light/dark adaptation — media block present, light fallbacks
        // inline so style-stripping clients (older Outlook) still render.
        assertTrue(html.contains("@media (prefers-color-scheme: dark)"));
        assertTrue(html.contains("name=\"color-scheme\" content=\"light dark\""));
        assertTrue(html.contains("background-color:#f4f4f5"));        // light canvas (inline)
        assertTrue(html.contains("background-color: #0a0a0d"));       // dark canvas (media)
        assertTrue(html.contains("color: #f87171"));                  // dark-mode link red

        // Written preview so the rendered email can be opened in a browser.
        Path out = Path.of("target", "email-preview.html");
        Files.createDirectories(out.getParent());
        Files.writeString(out, html);
    }
}
