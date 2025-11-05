package com.gephub.kyc_service.web;

import com.gephub.kyc_service.domain.KycResult;
import com.gephub.kyc_service.domain.KycSession;
import com.gephub.kyc_service.repository.KycResultRepository;
import com.gephub.kyc_service.repository.KycSessionRepository;
import com.gephub.kyc_service.service.WebhookService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import static com.gephub.kyc_service.security.AuthzUtil.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kyc")
public class KycResultController {
    private final KycResultRepository resultRepository;
    private final KycSessionRepository sessionRepository;
    private final WebhookService webhookService;

    private final String workerToken;

    public KycResultController(KycResultRepository resultRepository, KycSessionRepository sessionRepository, WebhookService webhookService,
                               @Value("${gephub.kyc.workerToken:}") String workerToken) {
        this.resultRepository = resultRepository;
        this.sessionRepository = sessionRepository;
        this.webhookService = webhookService;
        this.workerToken = workerToken;
    }

    @GetMapping("/sessions/{id}/result")
    public ResponseEntity<?> getResult(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var s = sessionRepository.findById(id).orElseThrow();
        var tokenOrg = orgId(jwt);
        if (tokenOrg != null && !tokenOrg.equals(s.getOrganizationId())) return ResponseEntity.status(403).build();
        if (!hasScope(jwt, "kyc.result:read") && !hasScope(jwt, "kyc.*")) return ResponseEntity.status(403).build();
        return resultRepository.findBySessionId(id)
                .<ResponseEntity<?>>map(r -> ResponseEntity.ok(Map.of(
                        "sessionId", r.getSessionId().toString(),
                        "livenessScore", r.getLivenessScore(),
                        "manualReview", r.isManualReview(),
                        "finalizedAt", r.getFinalizedAt() == null ? null : r.getFinalizedAt().toString(),
                        "reasonCodes", r.getReasonCodes()
                )))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "Result not ready")));
    }

    // Internal completion hook (to be called by worker). Secure with mTLS/internal auth later.
    public record CompleteRequest(@NotNull UUID sessionId, Double livenessScore, String[] reasonCodes, Boolean manualReview, Double faceMatchScore) {}

    @PostMapping("/internal/complete")
    public ResponseEntity<?> complete(@RequestHeader HttpHeaders headers, @Valid @RequestBody CompleteRequest req) {
        if (workerToken != null && !workerToken.isBlank()) {
            String auth = headers.getFirst("X-Gephub-Worker-Token");
            if (auth == null || !auth.equals(workerToken)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        KycSession s = sessionRepository.findById(req.sessionId()).orElseThrow();
        s.setStatus(req.livenessScore() != null && req.livenessScore() >= 0.7 ? "PASSED" : "FAILED");
        s.setUpdatedAt(OffsetDateTime.now());
        sessionRepository.save(s);

        KycResult r = resultRepository.findBySessionId(req.sessionId()).orElseGet(() -> {
            KycResult x = new KycResult();
            x.setId(UUID.randomUUID());
            x.setSessionId(req.sessionId());
            return x;
        });
        r.setLivenessScore(req.livenessScore());
        r.setFaceMatchScore(req.faceMatchScore());
        r.setReasonCodes(req.reasonCodes());
        r.setManualReview(Boolean.TRUE.equals(req.manualReview()));
        r.setFinalizedAt(OffsetDateTime.now());
        resultRepository.save(r);

        webhookService.deliverKycCompleted(s.getOrganizationId(), s.getId(), s.getStatus(), r.getLivenessScore(), r.getFaceMatchScore());
        return ResponseEntity.ok(Map.of("status", s.getStatus(), "livenessScore", r.getLivenessScore(), "faceMatchScore", r.getFaceMatchScore()));
    }
}


