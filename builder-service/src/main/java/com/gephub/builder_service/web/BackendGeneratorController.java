package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.GeneratedBackend;
import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.GeneratedBackendRepository;
import com.gephub.builder_service.repository.ProjectRepository;
import com.gephub.builder_service.service.BackendGeneratorService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gephub.builder_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/builder")
public class BackendGeneratorController {
    private final BackendGeneratorService backendGen;
    private final ProjectRepository projects;
    private final GeneratedBackendRepository backends;

    public BackendGeneratorController(BackendGeneratorService backendGen, ProjectRepository projects, GeneratedBackendRepository backends) {
        this.backendGen = backendGen;
        this.projects = projects;
        this.backends = backends;
    }

    public record GenerateBackendRequest(List<String> features, String language) {}

    @PostMapping("/projects/{id}/backend/generate")
    @Transactional
    public ResponseEntity<?> generate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody GenerateBackendRequest req) {
        if (!hasScope(jwt, "builder.backend:generate")) return ResponseEntity.status(403).build();
        var project = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        try {
            Resource zip = backendGen.generateSpringBootProject(project, req.features() == null ? List.of("api") : req.features());
            Map<String, Object> endpoints = backendGen.parseEndpoints(req.features() == null ? List.of("api") : req.features());
            GeneratedBackend backend = new GeneratedBackend();
            backend.setId(UUID.randomUUID());
            backend.setProjectId(id);
            backend.setBackendType("spring-boot");
            backend.setEndpoints(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(endpoints).toString());
            backend.setCreatedAt(OffsetDateTime.now());
            backends.save(backend);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + project.getName().toLowerCase().replace(" ", "-") + "-backend.zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zip);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/projects/{id}/backend")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var project = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        return backends.findByProjectId(id)
                .<ResponseEntity<?>>map(b -> {
                    try {
                        Map<String, Object> endpoints = b.getEndpoints() == null ? Map.of() : new com.fasterxml.jackson.databind.ObjectMapper().readValue(b.getEndpoints(), Map.class);
                        return ResponseEntity.ok(Map.of(
                                "id", b.getId().toString(),
                                "backendType", b.getBackendType(),
                                "githubRepo", b.getGithubRepo() == null ? "" : b.getGithubRepo(),
                                "endpoints", endpoints
                        ));
                    } catch (Exception e) {
                        return ResponseEntity.ok(Map.of(
                                "id", b.getId().toString(),
                                "backendType", b.getBackendType(),
                                "githubRepo", b.getGithubRepo() == null ? "" : b.getGithubRepo(),
                                "endpoints", List.of()
                        ));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("message", "No backend generated")));
    }
}

