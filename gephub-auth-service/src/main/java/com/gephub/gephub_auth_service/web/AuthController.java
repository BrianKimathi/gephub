package com.gephub.gephub_auth_service.web;

import com.gephub.gephub_auth_service.domain.User;
import com.gephub.gephub_auth_service.domain.Membership;
import com.gephub.gephub_auth_service.domain.OrganizationRole;
import com.gephub.gephub_auth_service.domain.ApiKey;
import com.gephub.gephub_auth_service.service.JwtService;
import com.gephub.gephub_auth_service.service.UserService;
import com.gephub.gephub_auth_service.service.ApiKeyService;
import com.gephub.gephub_auth_service.repository.MembershipRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final MembershipRepository membershipRepository;
    private final ApiKeyService apiKeyService;

    public AuthController(UserService userService, JwtService jwtService, MembershipRepository membershipRepository, ApiKeyService apiKeyService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.membershipRepository = membershipRepository;
        this.apiKeyService = apiKeyService;
    }

    public record RegisterRequest(@Email String email, @NotBlank String password, String organizationName) {}

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req.email(), req.password(), req.organizationName());
        return ResponseEntity.ok(Map.of("userId", user.getId().toString(), "email", user.getEmail()));
    }

    public record LoginRequest(@Email String email, @NotBlank String password) {}

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        User user = userService.authenticate(req.email(), req.password());
        var memberships = membershipRepository.findByUser(user.getId());
        UUID orgId = memberships.isEmpty() ? null : memberships.get(0).getId().getOrganizationId();
        OrganizationRole role = memberships.isEmpty() ? OrganizationRole.READONLY : memberships.get(0).getRole();
        java.util.Set<String> scopes = new java.util.HashSet<>();
        if (role == OrganizationRole.READONLY) {
            scopes.add("kyc.session:read");
            scopes.add("kyc.result:read");
            scopes.add("meets.room:join");
            scopes.add("builder.project:read");
        } else {
            scopes.add("kyc.*");
            scopes.add("meets.*");
            scopes.add("builder.*");
        }
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("email", user.getEmail());
        if (orgId != null) claims.put("org_id", orgId.toString());
        claims.put("role", role.name());
        claims.put("scopes", new java.util.ArrayList<>(scopes));
        String token = jwtService.issueToken(user.getId().toString(), claims, 900);
        return ResponseEntity.ok(Map.of("access_token", token, "token_type", "Bearer", "expires_in", 900, "org_id", orgId == null ? null : orgId.toString(), "role", role.name(), "scopes", new java.util.ArrayList<>(scopes)));
    }

    public record ApiKeyTokenRequest(String apiKey) {}

    @PostMapping("/api-key/token")
    public ResponseEntity<?> apiKeyToToken(@RequestBody ApiKeyTokenRequest req) {
        if (req == null || req.apiKey() == null || req.apiKey().isBlank()) return ResponseEntity.badRequest().build();
        ApiKey apiKey = apiKeyService.verifyAndLoad(req.apiKey());
        // Derive scopes from products
        boolean kyc = apiKey.getProducts().stream().anyMatch(p -> "kyc".equalsIgnoreCase(p.getCode()));
        boolean meets = apiKey.getProducts().stream().anyMatch(p -> "meets".equalsIgnoreCase(p.getCode()));
        boolean builder = apiKey.getProducts().stream().anyMatch(p -> "builder".equalsIgnoreCase(p.getCode()));
        java.util.List<String> scopes = new java.util.ArrayList<>();
        if (kyc) scopes.add("kyc.*");
        if (meets) scopes.add("meets.*");
        if (builder) scopes.add("builder.*");
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("org_id", apiKey.getOrganization().getId().toString());
        claims.put("scopes", scopes);
        claims.put("role", "DEV");
        String subject = "apiKey:" + apiKey.getId();
        String token = jwtService.issueToken(subject, claims, 900);
        return ResponseEntity.ok(Map.of("access_token", token, "token_type", "Bearer", "expires_in", 900, "org_id", apiKey.getOrganization().getId().toString(), "scopes", scopes));
    }
}


