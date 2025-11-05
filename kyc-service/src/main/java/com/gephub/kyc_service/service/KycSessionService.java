package com.gephub.kyc_service.service;

import com.gephub.kyc_service.domain.KycMedia;
import com.gephub.kyc_service.domain.KycSession;
import com.gephub.kyc_service.repository.KycMediaRepository;
import com.gephub.kyc_service.repository.KycSessionRepository;
import jakarta.transaction.Transactional;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class KycSessionService {
    private final KycSessionRepository sessionRepository;
    private final KycMediaRepository mediaRepository;
    private final StorageService storageService;
    private final long defaultTtlSeconds;
    private final RabbitTemplate rabbitTemplate;
    private final Queue kycQueue;

    public KycSessionService(KycSessionRepository sessionRepository, KycMediaRepository mediaRepository, StorageService storageService,
                             @Value("${gephub.kyc.sessionTtlSeconds:900}") long defaultTtlSeconds,
                             RabbitTemplate rabbitTemplate, Queue kycQueue) {
        this.sessionRepository = sessionRepository;
        this.mediaRepository = mediaRepository;
        this.storageService = storageService;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.rabbitTemplate = rabbitTemplate;
        this.kycQueue = kycQueue;
    }

    @Transactional
    public KycSession createSession(UUID organizationId, String userRef, String createdBy, Map<String, Object> challengeScript) {
        KycSession s = new KycSession();
        s.setId(UUID.randomUUID());
        s.setOrganizationId(organizationId);
        s.setUserRef(userRef);
        s.setStatus("PENDING");
        s.setChallengeScript(JsonUtil.toJson(challengeScript));
        s.setExpiresAt(OffsetDateTime.now().plusSeconds(defaultTtlSeconds));
        s.setCreatedBy(createdBy);
        return sessionRepository.save(s);
    }

    @Transactional
    public KycMedia saveMedia(UUID sessionId, String mediaType, MultipartFile file, String filename) throws IOException {
        KycSession s = sessionRepository.findById(sessionId).orElseThrow();
        if (s.getExpiresAt().isBefore(OffsetDateTime.now())) throw new IllegalStateException("Session expired");
        // Validate mediaType
        if (!java.util.Set.of("id_front", "id_back", "selfie_video", "selfie_frame").contains(mediaType)) {
            throw new IllegalArgumentException("Unsupported mediaType");
        }
        s.setStatus("UPLOADING");
        sessionRepository.save(s);

        // Validate mime/size
        String ct = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if ((mediaType.startsWith("id_") && !(ct.equals("image/jpeg") || ct.equals("image/png"))) ||
            (mediaType.startsWith("selfie_") && !(ct.equals("video/mp4") || ct.equals("image/jpeg")))) {
            throw new IllegalArgumentException("Invalid content type for " + mediaType);
        }

        var saved = storageService.save(sessionId, file, filename);

        KycMedia m = new KycMedia();
        m.setId(UUID.randomUUID());
        m.setSessionId(sessionId);
        m.setMediaType(mediaType);
        m.setFilePath(saved.filePath());
        m.setMimeType(saved.mimeType());
        m.setChecksum(saved.checksum());
        m.setSizeBytes(saved.sizeBytes());
        mediaRepository.save(m);

        // Enqueue only when required media present
        boolean hasFront = mediaRepository.existsBySessionIdAndMediaType(sessionId, "id_front");
        boolean hasBack = mediaRepository.existsBySessionIdAndMediaType(sessionId, "id_back");
        boolean hasSelfie = mediaRepository.existsBySessionIdAndMediaType(sessionId, "selfie_video") || mediaRepository.existsBySessionIdAndMediaType(sessionId, "selfie_frame");
        if (hasFront && hasBack && hasSelfie) {
            s.setStatus("PROCESSING");
            sessionRepository.save(s);
            java.util.List<String> prompts;
            try {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(s.getChallengeScript());
                prompts = new java.util.ArrayList<>();
                if (node.has("prompts") && node.get("prompts").isArray()) {
                    node.get("prompts").forEach(n -> prompts.add(n.asText()));
                } else {
                    prompts = java.util.List.of("look_left","look_right","look_up","look_down");
                }
            } catch (Exception e) {
                prompts = java.util.List.of("look_left","look_right","look_up","look_down");
            }
            rabbitTemplate.convertAndSend(kycQueue.getName(), Map.of(
                "sessionId", sessionId.toString(),
                "prompts", prompts
            ));
        }
        return m;
    }

    public record JsonUtil() {
        public static String toJson(Map<String, Object> map) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid challenge script", e);
            }
        }
    }
}


