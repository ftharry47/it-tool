package com.alignedcardio.itsm.service.notification;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class CommentEmailPreviewTest {

    @Test
    void printCommentEmailHtml() {
        NotificationTemplateBuilder builder = new NotificationTemplateBuilder("https://itsm.example.com");

        Map<String, Object> newComment = Map.of(
                "authorName", "Jane Smith",
                "body", "The new router is on order and will arrive tomorrow.",
                "createdAt", OffsetDateTime.now(),
                "public", true);

        Map<String, Object> prior = Map.of(
                "authorName", "John Doe",
                "body", "Can we get an ETA on the replacement hardware?",
                "createdAt", OffsetDateTime.now().minusHours(2),
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
        System.out.println(content.emailHtmlBody());
    }
}
