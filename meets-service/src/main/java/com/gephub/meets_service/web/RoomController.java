package com.gephub.meets_service.web;

import com.gephub.meets_service.domain.Participant;
import com.gephub.meets_service.domain.Room;
import com.gephub.meets_service.repository.ParticipantRepository;
import com.gephub.meets_service.repository.RoomRepository;
import com.gephub.meets_service.service.LiveKitTokenService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.gephub.meets_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/meets")
public class RoomController {
    private final RoomRepository rooms;
    private final ParticipantRepository participants;
    private final LiveKitTokenService tokenService;

    public RoomController(RoomRepository rooms, ParticipantRepository participants, LiveKitTokenService tokenService) {
        this.rooms = rooms;
        this.participants = participants;
        this.tokenService = tokenService;
    }

    public record CreateRoomRequest(@NotBlank String name, @Size(min=4,max=16) String code, Integer maxParticipants, UUID organizationId) {}

    @PostMapping("/rooms")
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateRoomRequest req) {
        if (!hasScope(jwt, "meets.room:create")) return ResponseEntity.status(403).build();
        UUID org = orgId(jwt);
        if (req.organizationId() != null && org != null && !org.equals(req.organizationId())) return ResponseEntity.status(403).build();
        UUID orgId = req.organizationId() != null ? req.organizationId() : org;
        if (orgId == null) return ResponseEntity.badRequest().body(Map.of("message","org_id required"));
        Room r = new Room();
        r.setId(UUID.randomUUID());
        r.setOrganizationId(orgId);
        r.setName(req.name());
        r.setCode(req.code());
        r.setMaxParticipants(req.maxParticipants() == null ? 50 : req.maxParticipants());
        r.setCreatedBy(jwt.getSubject());
        rooms.save(r);
        return ResponseEntity.ok(Map.of("id", r.getId().toString(), "code", r.getCode()));
    }

    @GetMapping("/rooms/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var r = rooms.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(r.getOrganizationId())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of(
            "id", r.getId().toString(),
            "name", r.getName(),
            "code", r.getCode(),
            "maxParticipants", r.getMaxParticipants(),
            "createdAt", r.getCreatedAt().toString(),
            "endedAt", r.getEndedAt() == null ? null : r.getEndedAt().toString()
        ));
    }

    public record JoinTokenRequest(@NotBlank String displayName, @NotBlank String role) {}

    @PostMapping("/rooms/{id}/participants/token")
    @Transactional
    public ResponseEntity<?> joinToken(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody JoinTokenRequest req) {
        if (!hasScope(jwt, "meets.room:join")) return ResponseEntity.status(403).build();
        var r = rooms.findById(id).orElseThrow(() -> new IllegalArgumentException("Room not found"));
        UUID org = orgId(jwt);
        if (org != null && !org.equals(r.getOrganizationId())) return ResponseEntity.status(403).build();
        if (r.getEndedAt() != null) return ResponseEntity.badRequest().body(Map.of("message", "Room ended"));
        long current = participants.countByRoomIdAndLeftAtIsNull(id);
        if (current >= r.getMaxParticipants()) return ResponseEntity.badRequest().body(Map.of("message", "Room full"));
        String userId = jwt.getSubject();
        String token = tokenService.createAccessToken(id, userId, req.displayName(), req.role());
        Participant p = new Participant();
        p.setId(UUID.randomUUID());
        p.setRoomId(id);
        p.setUserId(userId);
        p.setDisplayName(req.displayName());
        p.setRole(req.role());
        participants.save(p);
        return ResponseEntity.ok(Map.of("accessToken", token, "roomId", id.toString(), "participantId", p.getId().toString()));
    }

    @PostMapping("/rooms/{id}/end")
    @Transactional
    public ResponseEntity<?> endRoom(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!hasScope(jwt, "meets.room:moderate")) return ResponseEntity.status(403).build();
        var r = rooms.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(r.getOrganizationId())) return ResponseEntity.status(403).build();
        r.setEndedAt(OffsetDateTime.now());
        rooms.save(r);
        return ResponseEntity.ok(Map.of("status", "ended"));
    }
}


