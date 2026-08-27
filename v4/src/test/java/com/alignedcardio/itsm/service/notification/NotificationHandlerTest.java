package com.alignedcardio.itsm.service.notification;

import com.alignedcardio.itsm.entity.Notification;
import com.alignedcardio.itsm.event.IncidentCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationHandlerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sendNotificationActionRoutesToCorrectUser() throws Exception {
        NotificationHandler handler = new NotificationHandler(notificationService);

        UUID orgId = UUID.randomUUID();
        UUID incidentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        JsonNode action = objectMapper.readTree("""
                {
                  "userId": "%s",
                  "subject": "Assigned to you",
                  "body": "You have a new incident",
                  "channel": "in_app"
                }
                """.formatted(userId));

        IncidentCreatedEvent event = new IncidentCreatedEvent(
                orgId, incidentId, Map.of("status", "NEW"));

        handler.handle(event, action, UUID.randomUUID());

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());

        NotificationRequest request = captor.getValue();
        assertEquals(userId, request.userId());
        assertEquals("Assigned to you", request.subject());
        assertEquals("You have a new incident", request.body());
        assertEquals(Notification.Channel.IN_APP, request.channel());
        assertEquals("INCIDENT", request.entityType());
        assertEquals(incidentId, request.entityId());
    }
}
