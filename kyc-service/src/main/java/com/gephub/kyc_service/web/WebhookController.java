package com.gephub.kyc_service.web;

import com.gephub.kyc_service.domain.WebhookEndpoint;
import com.gephub.kyc_service.repository.WebhookEndpointRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gephub.kyc_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/kyc/webhooks")
public class WebhookController {
    private final WebhookEndpointRepository repo;

    public WebhookController(WebhookEndpointRepository repo) {
        this.repo = repo;
    }

    public record UpsertRequest(@NotNull UUID organizationId, @NotBlank String url, @NotBlank String secret, List<String> events) {}

    @PostMapping
    public ResponseEntity<?> upsert(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpsertRequest req) {
        if (!hasScope(jwt, "kyc.webhook:write") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        String r = role(jwt);
        if (!roleAtLeast(r, "ADMIN")) return ResponseEntity.status(403).build();
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(req.organizationId())) return ResponseEntity.status(403).build();

        WebhookEndpoint w = new WebhookEndpoint();
        w.setId(UUID.randomUUID());
        w.setOrganizationId(req.organizationId());
        w.setUrl(req.url());
        w.setSecret(req.secret());
        w.setEvents(req.events() == null ? new String[]{"kyc.session.completed"} : req.events().toArray(new String[0]));
        w.setActive(true);
        repo.save(w);
        return ResponseEntity.ok(Map.of("id", w.getId().toString()));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID organizationId) {
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(organizationId)) return ResponseEntity.status(403).build();
        if (!hasScope(jwt, "kyc.webhook:read") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        var items = repo.findByOrganizationIdAndActiveTrue(organizationId).stream().map(w -> Map.of(
            "id", w.getId().toString(),
            "url", w.getUrl(),
            "events", List.of(w.getEvents()),
            "active", w.isActive()
        )).toList();
        return ResponseEntity.ok(items);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!hasScope(jwt, "kyc.webhook:write") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        String r = role(jwt);
        if (!roleAtLeast(r, "ADMIN")) return ResponseEntity.status(403).build();
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}


