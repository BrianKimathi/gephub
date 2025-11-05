package com.gephub.builder_service.web;

import com.gephub.builder_service.service.AiService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static com.gephub.builder_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/builder/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    public record GenerateRequest(@NotBlank String prompt, String context, UUID projectId) {}

    @PostMapping("/generate")
    public ResponseEntity<?> generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GenerateRequest req) {
        if (!hasScope(jwt, "builder.project:create")) return ResponseEntity.status(403).build();
        Map<String, Object> componentTree = aiService.generateComponentTree(req.prompt(), req.context());
        return ResponseEntity.ok(Map.of("componentTree", componentTree, "suggestions", aiService.suggestComponents(req.context())));
    }

    @GetMapping("/suggest")
    public ResponseEntity<?> suggest(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String context) {
        return ResponseEntity.ok(Map.of("components", aiService.suggestComponents(context)));
    }
}

