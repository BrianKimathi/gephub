package com.gephub.kyc_service.web;

import com.gephub.kyc_service.domain.KycMedia;
import com.gephub.kyc_service.domain.KycSession;
import com.gephub.kyc_service.repository.KycMediaRepository;
import com.gephub.kyc_service.repository.KycSessionRepository;
import com.gephub.kyc_service.service.KycSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static com.gephub.kyc_service.security.AuthzUtil.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycSessionController {
    private final KycSessionService service;
    private final KycSessionRepository sessionRepository;
    private final KycMediaRepository mediaRepository;

    public KycSessionController(KycSessionService service, KycSessionRepository sessionRepository, KycMediaRepository mediaRepository) {
        this.service = service;
        this.sessionRepository = sessionRepository;
        this.mediaRepository = mediaRepository;
    }

    public record CreateSessionRequest(@NotNull UUID organizationId, String userRef, Map<String, Object> challengeScript) {}

    @PostMapping("/sessions")
    public ResponseEntity<?> createSession(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateSessionRequest req) {
        // RBAC + scope checks
        if (!hasScope(jwt, "kyc.session:create") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        String r = role(jwt);
        if (!roleAtLeast(r, "DEV")) return ResponseEntity.status(403).build();
        // Org isolation: if token has org_id, it must match requested org
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(req.organizationId())) return ResponseEntity.status(403).build();
        String createdBy = jwt.getSubject();
        Map<String, Object> script = req.challengeScript() == null ? Map.of("prompts", List.of("look_left","look_right","look_up","look_down"), "segmentSeconds", 2) : req.challengeScript();
        KycSession s = service.createSession(req.organizationId(), req.userRef(), createdBy, script);
        return ResponseEntity.ok(Map.of(
            "sessionId", s.getId().toString(),
            "status", s.getStatus(),
            "expiresAt", s.getExpiresAt().toString(),
            "challengeScript", script
        ));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<?> getSession(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        KycSession s = sessionRepository.findById(id).orElseThrow();
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(s.getOrganizationId())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of(
            "sessionId", s.getId().toString(),
            "status", s.getStatus(),
            "expiresAt", s.getExpiresAt().toString()
        ));
    }

    @PostMapping(value = "/sessions/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMedia(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
                                         @RequestParam("mediaType") @NotBlank String mediaType,
                                         @RequestPart("file") MultipartFile file) throws IOException {
        if (!hasScope(jwt, "kyc.media:upload") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        String r = role(jwt);
        if (!roleAtLeast(r, "DEV")) return ResponseEntity.status(403).build();
        KycSession s0 = sessionRepository.findById(id).orElseThrow();
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(s0.getOrganizationId())) return ResponseEntity.status(403).build();
        String filename = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        KycMedia m = service.saveMedia(id, mediaType, file, filename);
        return ResponseEntity.ok(Map.of(
            "mediaId", m.getId().toString(),
            "checksum", m.getChecksum(),
            "sizeBytes", m.getSizeBytes()
        ));
    }
}


