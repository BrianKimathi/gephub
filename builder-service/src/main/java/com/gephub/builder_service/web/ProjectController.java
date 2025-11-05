package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.Project;
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
public class ProjectController {
    private final ProjectRepository projects;

    public ProjectController(ProjectRepository projects) {
        this.projects = projects;
    }

    public record CreateProjectRequest(@NotBlank String name, String description, String type, UUID organizationId) {}

    @PostMapping("/projects")
    @Transactional
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateProjectRequest req) {
        if (!hasScope(jwt, "builder.project:create")) return ResponseEntity.status(403).build();
        UUID org = orgId(jwt);
        if (req.organizationId() != null && org != null && !org.equals(req.organizationId())) return ResponseEntity.status(403).build();
        UUID orgId = req.organizationId() != null ? req.organizationId() : org;
        if (orgId == null) return ResponseEntity.badRequest().body(Map.of("message", "org_id required"));
        Project p = new Project();
        p.setId(UUID.randomUUID());
        p.setOrganizationId(orgId);
        p.setName(req.name());
        p.setDescription(req.description());
        p.setType(req.type() == null ? "website" : req.type());
        p.setCreatedBy(jwt.getSubject());
        projects.save(p);
        return ResponseEntity.ok(Map.of("id", p.getId().toString(), "name", p.getName(), "status", p.getStatus()));
    }

    @GetMapping("/projects")
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) UUID organizationId) {
        UUID org = orgId(jwt);
        UUID targetOrg = organizationId != null ? organizationId : org;
        if (targetOrg == null) return ResponseEntity.badRequest().body(Map.of("message", "org_id required"));
        if (org != null && !org.equals(targetOrg)) return ResponseEntity.status(403).build();
        List<Project> list = projects.findByOrganizationId(targetOrg);
        return ResponseEntity.ok(list.stream().map(p -> Map.of(
                "id", p.getId().toString(),
                "name", p.getName(),
                "type", p.getType(),
                "status", p.getStatus(),
                "createdAt", p.getCreatedAt().toString()
        )).toList());
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var p = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(p.getOrganizationId())) return ResponseEntity.status(403).build();
        return ResponseEntity.ok(Map.of(
                "id", p.getId().toString(),
                "name", p.getName(),
                "description", p.getDescription() == null ? "" : p.getDescription(),
                "type", p.getType(),
                "status", p.getStatus(),
                "createdAt", p.getCreatedAt().toString()
        ));
    }

    @PutMapping("/projects/{id}")
    @Transactional
    public ResponseEntity<?> update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody Map<String, Object> updates) {
        var p = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(p.getOrganizationId())) return ResponseEntity.status(403).build();
        if (updates.containsKey("name")) p.setName(updates.get("name").toString());
        if (updates.containsKey("description")) p.setDescription(updates.get("description").toString());
        if (updates.containsKey("status")) p.setStatus(updates.get("status").toString());
        p.setUpdatedAt(OffsetDateTime.now());
        projects.save(p);
        return ResponseEntity.ok(Map.of("id", p.getId().toString(), "status", "updated"));
    }

    @DeleteMapping("/projects/{id}")
    @Transactional
    public ResponseEntity<?> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var p = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(p.getOrganizationId())) return ResponseEntity.status(403).build();
        projects.delete(p);
        return ResponseEntity.noContent().build();
    }
}

