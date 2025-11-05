package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.WebhookEndpoint;
import com.gephub.builder_service.repository.WebhookEndpointRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gephub.builder_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/builder/webhooks")
public class WebhookController {
    private final WebhookEndpointRepository webhooks;

    public WebhookController(WebhookEndpointRepository webhooks) {
        this.webhooks = webhooks;
    }

    public record CreateWebhookRequest(@NotBlank String url, List<String> eventTypes, String secret) {}

    @PostMapping
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateWebhookRequest req) {
        if (!hasScope(jwt, "builder.webhook:create")) return ResponseEntity.status(403).build();
        UUID org = orgId(jwt);
        if (org == null) return ResponseEntity.badRequest().body(Map.of("message", "org_id required"));
        
        WebhookEndpoint endpoint = new WebhookEndpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrganizationId(org);
        endpoint.setUrl(req.url());
        if (req.eventTypes() != null) {
            endpoint.setEventTypes(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(req.eventTypes()).toString());
        } else {
            endpoint.setEventTypes("[\"*\"]");
        }
        endpoint.setSecret(req.secret());
        webhooks.save(endpoint);
        return ResponseEntity.ok(Map.of("id", endpoint.getId().toString(), "url", endpoint.getUrl()));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt) {
        UUID org = orgId(jwt);
        if (org == null) return ResponseEntity.badRequest().body(Map.of("message", "org_id required"));
        List<WebhookEndpoint> list = webhooks.findByOrganizationIdAndIsActive(org, true);
        return ResponseEntity.ok(list.stream().map(w -> {
            try {
                List<String> eventTypes = w.getEventTypes() == null ? List.of("*") : new com.fasterxml.jackson.databind.ObjectMapper().readValue(w.getEventTypes(), List.class);
                return Map.of(
                        "id", w.getId().toString(),
                        "url", w.getUrl(),
                        "eventTypes", eventTypes,
                        "isActive", w.getIsActive()
                );
            } catch (Exception e) {
                return Map.of(
                        "id", w.getId().toString(),
                        "url", w.getUrl(),
                        "eventTypes", List.of("*"),
                        "isActive", w.getIsActive()
                );
            }
        }).toList());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var endpoint = webhooks.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(endpoint.getOrganizationId())) return ResponseEntity.status(403).build();
        endpoint.setIsActive(false);
        webhooks.save(endpoint);
        return ResponseEntity.noContent().build();
    }
}

