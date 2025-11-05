package com.gephub.builder_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gephub.builder_service.domain.WebhookEndpoint;
import com.gephub.builder_service.repository.WebhookEndpointRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {
    private final WebhookEndpointRepository webhooks;
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    public WebhookService(WebhookEndpointRepository webhooks) {
        this.webhooks = webhooks;
    }

    @Async
    public void sendWebhook(UUID organizationId, String eventType, Map<String, Object> payload) {
        List<WebhookEndpoint> endpoints = webhooks.findByOrganizationIdAndIsActive(organizationId, true);
        
        for (WebhookEndpoint endpoint : endpoints) {
            try {
                // Check if endpoint subscribes to this event type
                if (endpoint.getEventTypes() != null) {
                    List<String> eventTypes = json.readValue(endpoint.getEventTypes(), List.class);
                    if (!eventTypes.contains(eventType) && !eventTypes.contains("*")) {
                        continue;
                    }
                }
                
                Map<String, Object> webhookPayload = new HashMap<>();
                webhookPayload.put("event", eventType);
                webhookPayload.put("timestamp", System.currentTimeMillis() / 1000);
                webhookPayload.put("data", payload);
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                // Add HMAC signature if secret is configured
                if (endpoint.getSecret() != null && !endpoint.getSecret().isBlank()) {
                    String signature = calculateSignature(endpoint.getSecret(), json.writeValueAsString(webhookPayload));
                    headers.set("X-Gephub-Signature", signature);
                }
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(webhookPayload, headers);
                http.postForEntity(endpoint.getUrl(), entity, String.class);
                
            } catch (Exception e) {
                System.err.println("Failed to send webhook to " + endpoint.getUrl() + ": " + e.getMessage());
            }
        }
    }

    private String calculateSignature(String secret, String payload) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKey);
        byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }
}

