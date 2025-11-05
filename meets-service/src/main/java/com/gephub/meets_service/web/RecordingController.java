package com.gephub.meets_service.web;

import com.gephub.meets_service.domain.Recording;
import com.gephub.meets_service.domain.Room;
import com.gephub.meets_service.repository.RecordingRepository;
import com.gephub.meets_service.repository.RoomRepository;
import com.gephub.meets_service.service.RecordingService;
import jakarta.transaction.Transactional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gephub.meets_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/meets")
public class RecordingController {
    private final RecordingService recordingService;
    private final RecordingRepository recordings;
    private final RoomRepository rooms;

    public RecordingController(RecordingService recordingService, RecordingRepository recordings, RoomRepository rooms) {
        this.recordingService = recordingService;
        this.recordings = recordings;
        this.rooms = rooms;
    }

    @PostMapping("/rooms/{id}/recordings/start")
    @Transactional
    public ResponseEntity<?> startRecording(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!hasScope(jwt, "meets.recording:create")) return ResponseEntity.status(403).build();
        var room = rooms.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(room.getOrganizationId())) return ResponseEntity.status(403).build();
        if (room.getEndedAt() != null) return ResponseEntity.badRequest().body(Map.of("message", "Room ended"));
        
        // Check if recording already in progress
        var existing = recordings.findByRoomIdAndStatus(id, "recording");
        if (existing.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Recording already in progress", "recordingId", existing.get().getId().toString()));
        }
        
        try {
            String recordingId = recordingService.startRecording(id);
            Recording rec = new Recording();
            rec.setId(UUID.randomUUID());
            rec.setRoomId(id);
            rec.setPath(recordingService.getRecordingPath(id, recordingId));
            rec.setStatus("recording");
            recordings.save(rec);
            return ResponseEntity.ok(Map.of("recordingId", rec.getId().toString(), "status", "recording"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to start recording: " + e.getMessage()));
        }
    }

    @PostMapping("/rooms/{id}/recordings/stop")
    @Transactional
    public ResponseEntity<?> stopRecording(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        if (!hasScope(jwt, "meets.recording:moderate")) return ResponseEntity.status(403).build();
        var room = rooms.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(room.getOrganizationId())) return ResponseEntity.status(403).build();
        
        var recording = recordings.findByRoomIdAndStatus(id, "recording").orElse(null);
        if (recording == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "No active recording"));
        }
        
        try {
            recordingService.stopRecording(id, recording.getId().toString());
            recording.setStatus("processing");
            recordings.save(recording);
            return ResponseEntity.ok(Map.of("recordingId", recording.getId().toString(), "status", "processing"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to stop recording: " + e.getMessage()));
        }
    }

    @GetMapping("/rooms/{id}/recordings")
    public ResponseEntity<?> listRecordings(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var room = rooms.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(room.getOrganizationId())) return ResponseEntity.status(403).build();
        
        List<Recording> list = recordings.findByRoomId(id);
        return ResponseEntity.ok(list.stream().map(r -> Map.of(
                "id", r.getId().toString(),
                "status", r.getStatus(),
                "path", r.getPath(),
                "bytes", r.getBytes(),
                "durationSeconds", r.getDurationSeconds() == null ? 0 : r.getDurationSeconds(),
                "createdAt", r.getCreatedAt().toString(),
                "completedAt", r.getCompletedAt() == null ? null : r.getCompletedAt().toString()
        )).toList());
    }

    @GetMapping("/recordings/{recordingId}/download")
    public ResponseEntity<Resource> downloadRecording(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID recordingId) {
        var recording = recordings.findById(recordingId).orElseThrow();
        var room = rooms.findById(recording.getRoomId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(room.getOrganizationId())) return ResponseEntity.status(403).build();
        
        if (!"completed".equals(recording.getStatus())) {
            return ResponseEntity.badRequest().build();
        }
        
        File file = new File(recording.getPath());
        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }
        
        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"recording-" + recordingId + ".mp4\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/recordings/{recordingId}/complete")
    @Transactional
    public ResponseEntity<?> completeRecording(@PathVariable UUID recordingId, 
                                              @RequestBody Map<String, Object> payload) {
        // This endpoint is called by LiveKit webhook when recording completes
        // In production, verify webhook signature
        var recording = recordings.findById(recordingId).orElse(null);
        if (recording == null) return ResponseEntity.notFound().build();
        
        recording.setStatus("completed");
        if (payload.containsKey("bytes")) recording.setBytes(Long.valueOf(payload.get("bytes").toString()));
        if (payload.containsKey("duration")) recording.setDurationSeconds(Integer.valueOf(payload.get("duration").toString()));
        recording.setCompletedAt(OffsetDateTime.now());
        recordings.save(recording);
        return ResponseEntity.ok(Map.of("status", "updated"));
    }
}

