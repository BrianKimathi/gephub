package com.gephub.gephub_auth_service.web;

import com.gephub.gephub_auth_service.domain.Organization;
import com.gephub.gephub_auth_service.service.ApiKeyService;
import com.gephub.gephub_auth_service.repository.OrganizationRepository;
import com.gephub.gephub_auth_service.repository.MembershipRepository;
import com.gephub.gephub_auth_service.domain.OrganizationRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/apikeys")
public class ApiKeyController {
    private final ApiKeyService apiKeyService;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;

    public ApiKeyController(ApiKeyService apiKeyService, OrganizationRepository organizationRepository, MembershipRepository membershipRepository) {
        this.apiKeyService = apiKeyService;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
    }

    public record CreateKeyRequest(
        @Pattern(regexp = "^(test|live)$", message = "environment must be 'test' or 'live'") String environment,
        @NotEmpty List<String> products,
        UUID organizationId
    ) {}

    @PostMapping
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateKeyRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var membership = membershipRepository.find(userId, req.organizationId());
        if (!membershipRepository.hasAtLeastRole(membership, OrganizationRole.ADMIN)) {
            return ResponseEntity.status(403).build();
        }
        Organization org = organizationRepository.findById(req.organizationId()).orElseThrow();
        var gen = apiKeyService.createKey(org, userId, req.environment(), req.products());
        return ResponseEntity.ok(Map.of(
            "id", gen.id().toString(),
            "apiKey", gen.prefix() + "." + gen.secret()
        ));
    }

    public record RevokeRequest(UUID id) {}

    @PostMapping("/revoke")
    public ResponseEntity<?> revoke(@AuthenticationPrincipal Jwt jwt, @RequestBody RevokeRequest req) {
        // TODO: enforce org ownership by looking up key's org and verifying role; simplified for now
        apiKeyService.revoke(req.id());
        return ResponseEntity.noContent().build();
    }

    public record RotateRequest(UUID id) {}

    @PostMapping("/rotate")
    public ResponseEntity<?> rotate(@AuthenticationPrincipal Jwt jwt, @RequestBody RotateRequest req) {
        var gen = apiKeyService.rotate(req.id());
        return ResponseEntity.ok(Map.of(
            "id", gen.id().toString(),
            "apiKey", gen.prefix() + "." + gen.secret()
        ));
    }

    @GetMapping
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID organizationId) {
        UUID userId = UUID.fromString(jwt.getSubject());
        var membership = membershipRepository.find(userId, organizationId);
        if (membership.isEmpty()) return ResponseEntity.status(403).build();
        var items = apiKeyService.listByOrganization(organizationId).stream().map(k -> Map.of(
            "id", k.getId().toString(),
            "organizationId", k.getOrganization().getId().toString(),
            "keyPrefix", k.getKeyPrefix(),
            "environment", k.getEnvironment(),
            "status", k.getStatus(),
            "products", k.getProducts().stream().map(p -> p.getCode()).toList(),
            "createdAt", k.getCreatedAt().toString(),
            "lastUsedAt", k.getLastUsedAt() == null ? null : k.getLastUsedAt().toString()
        )).toList();
        return ResponseEntity.ok(items);
    }
}


