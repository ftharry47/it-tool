package com.alignedcardio.itsm.service.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "azure.graph.enabled", havingValue = "true")
public class GraphMailClient {

    private final String tenantId;
    private final String clientId;
    private final String clientSecret;
    private final String fromMailbox;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile String accessToken;
    private volatile Instant tokenExpiry;

    public GraphMailClient(
            @Value("${azure.graph.tenant-id}") String tenantId,
            @Value("${azure.graph.client-id}") String clientId,
            @Value("${azure.graph.client-secret}") String clientSecret,
            @Value("${azure.graph.from-mailbox}") String fromMailbox,
            ObjectMapper objectMapper) {
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.fromMailbox = fromMailbox;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public void sendEmail(String to, String subject, String body) {
        sendEmail(to, subject, body, false);
    }

    public void sendEmail(String to, String subject, String body, boolean html) {
        String token = getAccessToken();
        try {
            String json = objectMapper.writeValueAsString(buildMessage(to, subject, body, html));
            restClient.post()
                    .uri("https://graph.microsoft.com/v1.0/users/{upn}/sendMail", fromMailbox)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            throw new GraphMailException("Failed to send email via Microsoft Graph", e);
        }
    }

    private Object buildMessage(String to, String subject, String body, boolean html) {
        return Map.of(
                "message", Map.of(
                        "subject", subject,
                        "body", Map.of("contentType", html ? "HTML" : "Text", "content", body),
                        "toRecipients", List.of(
                                Map.of("emailAddress", Map.of("address", to))
                        )
                ),
                "saveToSentItems", false
        );
    }

    private synchronized String getAccessToken() {
        if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
            return accessToken;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("scope", "https://graph.microsoft.com/.default");

        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        try {
            String response = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);
            accessToken = node.path("access_token").asText();
            int expiresIn = node.path("expires_in").asInt(3600);
            tokenExpiry = Instant.now().plusSeconds(expiresIn);
            return accessToken;
        } catch (Exception e) {
            throw new GraphMailException("Failed to obtain Microsoft Graph access token", e);
        }
    }

    public static class GraphMailException extends RuntimeException {
        public GraphMailException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
