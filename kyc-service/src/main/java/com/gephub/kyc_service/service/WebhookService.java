package com.gephub.kyc_service.service;

import com.gephub.kyc_service.domain.WebhookEndpoint;
import com.gephub.kyc_service.repository.WebhookEndpointRepository;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {
    private final WebhookEndpointRepository repo;
    private final RestTemplate http = new RestTemplate();

    public WebhookService(WebhookEndpointRepository repo) {
        this.repo = repo;
    }

    public void deliverKycCompleted(UUID organizationId, UUID sessionId, String status, Double livenessScore, Double faceMatchScore) {
        List<WebhookEndpoint> targets = repo.findByOrganizationIdAndActiveTrue(organizationId);
        Map<String, Object> payload = Map.of(
            "type", "kyc.session.completed",
            "data", Map.of(
                "sessionId", sessionId.toString(),
                "status", status,
                "livenessScore", livenessScore,
                "faceMatchScore", faceMatchScore
            )
        );
        String body = toJson(payload);
        for (WebhookEndpoint ep : targets) {
            try {
                String sig = sign(body, ep.getSecret());
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.add("X-Gephub-Signature", sig);
                http.postForEntity(ep.getUrl(), new HttpEntity<>(body, headers), String.class);
            } catch (Exception ignored) {
            }
        }
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String sign(String body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}


