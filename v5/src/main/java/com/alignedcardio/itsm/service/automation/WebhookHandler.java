package com.alignedcardio.itsm.service.automation;

import com.alignedcardio.itsm.event.DomainEvent;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class WebhookHandler {

    private final WebhookUrlValidator urlValidator;

    public WebhookHandler(WebhookUrlValidator urlValidator) {
        this.urlValidator = urlValidator;
    }

    public void handle(DomainEvent event, JsonNode action, UUID actingUserId) {
        String url = action.get("url").asText();
        String method = action.hasNonNull("method") ? action.get("method").asText().toUpperCase() : "GET";
        String body = action.hasNonNull("body") ? action.get("body").asText() : null;

        urlValidator.validate(url);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "ITSM-Automation/1.0");

        HttpRequest request;
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String payload = body != null ? body : "";
            request = requestBuilder
                    .method(method, HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .build();
        } else {
            request = requestBuilder.GET().build();
        }

        try {
            HttpResponse<String> response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();

            if (response.statusCode() >= 400) {
                throw new WebhookException(
                        "Webhook returned status " + response.statusCode() + ": " + url,
                        "HTTP " + response.statusCode());
            }
        } catch (java.util.concurrent.CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof TimeoutException) {
                throw new WebhookException("Webhook timed out: " + url, "timeout");
            }
            throw new WebhookException("Webhook failed: " + url + " - " + cause.getMessage(), cause.getMessage());
        }
    }
}
