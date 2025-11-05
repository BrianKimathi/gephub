package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.Page;
import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.PageRepository;
import com.gephub.builder_service.repository.ProjectRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class PageController {
    private final PageRepository pages;
    private final ProjectRepository projects;

    public PageController(PageRepository pages, ProjectRepository projects) {
        this.pages = pages;
        this.projects = projects;
    }

    public record CreatePageRequest(@NotBlank String name, @NotBlank String path, @NotBlank String componentTree, Map<String, Object> metadata) {}

    @PostMapping("/projects/{projectId}/pages")
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId, @Valid @RequestBody CreatePageRequest req) {
        var project = projects.findById(projectId).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        Page p = new Page();
        p.setId(UUID.randomUUID());
        p.setProjectId(projectId);
        p.setName(req.name());
        p.setPath(req.path());
        p.setComponentTree(req.componentTree());
        if (req.metadata() != null) {
            p.setMetadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(req.metadata()).toString());
        }
        pages.save(p);
        return ResponseEntity.ok(Map.of("id", p.getId().toString(), "name", p.getName(), "path", p.getPath()));
    }

    @GetMapping("/projects/{projectId}/pages")
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID projectId) {
        var project = projects.findById(projectId).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        List<Page> list = pages.findByProjectId(projectId);
        return ResponseEntity.ok(list.stream().map(p -> Map.of(
                "id", p.getId().toString(),
                "name", p.getName(),
                "path", p.getPath(),
                "createdAt", p.getCreatedAt().toString()
        )).toList());
    }

    @GetMapping("/pages/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var p = pages.findById(id).orElseThrow();
        var project = projects.findById(p.getProjectId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        try {
            Map<String, Object> metadata = p.getMetadata() == null ? Map.of() : new com.fasterxml.jackson.databind.ObjectMapper().readValue(p.getMetadata(), Map.class);
            return ResponseEntity.ok(Map.of(
                    "id", p.getId().toString(),
                    "name", p.getName(),
                    "path", p.getPath(),
                    "componentTree", p.getComponentTree(),
                    "metadata", metadata
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of(
                    "id", p.getId().toString(),
                    "name", p.getName(),
                    "path", p.getPath(),
                    "componentTree", p.getComponentTree(),
                    "metadata", Map.of()
            ));
        }
    }

    @PutMapping("/pages/{id}")
    @Transactional
    public ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        var p = pages.findById(id).orElseThrow();
        var project = projects.findById(p.getProjectId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        if (updates.containsKey("name")) p.setName(updates.get("name").toString());
        if (updates.containsKey("path")) p.setPath(updates.get("path").toString());
        if (updates.containsKey("componentTree")) p.setComponentTree(updates.get("componentTree").toString());
        if (updates.containsKey("metadata")) {
            p.setMetadata(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(updates.get("metadata")).toString());
        }
        p.setUpdatedAt(OffsetDateTime.now());
        pages.save(p);
        return ResponseEntity.ok(Map.of("id", p.getId().toString(), "status", "updated"));
    }

    @DeleteMapping("/pages/{id}")
    @Transactional
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var p = pages.findById(id).orElseThrow();
        var project = projects.findById(p.getProjectId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        pages.delete(p);
        return ResponseEntity.noContent().build();
    }
}

