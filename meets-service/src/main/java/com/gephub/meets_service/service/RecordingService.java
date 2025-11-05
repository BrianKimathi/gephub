package com.gephub.meets_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.livekit.server.LiveKitServer;
import io.livekit.server.RoomService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RecordingService {
    private final LiveKitServer liveKit;
    private final RoomService roomService;
    private final String recordingsRoot;
    private final String apiKey;
    private final String apiSecret;
    private final String host;
    private final RestTemplate http = new RestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    public RecordingService(@Value("${gephub.meets.livekit.apiKey}") String apiKey,
                           @Value("${gephub.meets.livekit.apiSecret}") String apiSecret,
                           @Value("${gephub.meets.livekit.host}") String host,
                           @Value("${gephub.meets.recordingsRoot}") String recordingsRoot) throws IOException {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.host = host;
        this.recordingsRoot = recordingsRoot;
        this.liveKit = new LiveKitServer(host, apiKey, apiSecret);
        this.roomService = liveKit.getRoomService();
        // Ensure recordings directory exists
        Path rootPath = Paths.get(recordingsRoot);
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath);
        }
    }

    public String startRecording(UUID roomId) throws Exception {
        String recordingId = UUID.randomUUID().toString();
        String recordingPath = Paths.get(recordingsRoot, roomId.toString(), recordingId + ".mp4").toString();
        
        // Ensure room directory exists
        Path roomDir = Paths.get(recordingsRoot, roomId.toString());
        if (!Files.exists(roomDir)) {
            Files.createDirectories(roomDir);
        }
        
        // Start recording via LiveKit HTTP API
        if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
            try {
                String url = host.replace("http://", "").replace("https://", "");
                String recordingUrl = "http://" + url + "/twirp/livekit.RecordingService/StartRecording";
                
                Map<String, Object> request = new HashMap<>();
                request.put("name", roomId.toString());
                request.put("output", "mp4");
                request.put("filepath", recordingPath);
                
                HttpHeaders headers = createAuthHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                ResponseEntity<Map> response = http.postForEntity(recordingUrl, entity, Map.class);
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> body = response.getBody();
                    if (body.containsKey("recording_id")) {
                        return body.get("recording_id").toString();
                    }
                }
            } catch (Exception e) {
                // Fallback to local tracking if API call fails
                System.err.println("LiveKit recording API call failed: " + e.getMessage());
            }
        }
        
        return recordingId;
    }

    public void stopRecording(UUID roomId, String recordingId) throws Exception {
        // Stop recording via LiveKit HTTP API
        if (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank()) {
            try {
                String url = host.replace("http://", "").replace("https://", "");
                String recordingUrl = "http://" + url + "/twirp/livekit.RecordingService/StopRecording";
                
                Map<String, Object> request = new HashMap<>();
                request.put("recording_id", recordingId);
                
                HttpHeaders headers = createAuthHeaders();
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);
                
                http.postForEntity(recordingUrl, entity, Map.class);
            } catch (Exception e) {
                System.err.println("LiveKit stop recording API call failed: " + e.getMessage());
            }
        }
    }

    private HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // LiveKit HTTP API uses API Key and Secret in Authorization header
        // Format: Authorization: Bearer <base64(apiKey:apiSecret)>
        String credentials = apiKey + ":" + apiSecret;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        headers.setBearerAuth(encoded);
        
        // Alternative: Use X-API-Key header
        headers.set("X-API-Key", apiKey);
        headers.set("X-API-Secret", apiSecret);
        
        return headers;
    }

    public String getRecordingPath(UUID roomId, String recordingId) {
        return Paths.get(recordingsRoot, roomId.toString(), recordingId + ".mp4").toString();
    }

    public void handleRecordingComplete(UUID roomId, String recordingId, long bytes, int durationSeconds) {
        // This would be called by a LiveKit webhook when recording completes
        // Update the recording entity with final status
    }
}

