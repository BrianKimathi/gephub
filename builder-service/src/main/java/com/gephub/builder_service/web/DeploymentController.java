package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.Deployment;
import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.DeploymentRepository;
import com.gephub.builder_service.repository.ProjectRepository;
import com.gephub.builder_service.service.DeploymentService;
import com.gephub.builder_service.service.GitHubService;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.gephub.builder_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/builder")
public class DeploymentController {
    private final DeploymentService deploymentService;
    private final GitHubService githubService;
    private final ProjectRepository projects;
    private final DeploymentRepository deployments;

    public DeploymentController(DeploymentService deploymentService, GitHubService githubService, ProjectRepository projects, DeploymentRepository deployments) {
        this.deploymentService = deploymentService;
        this.githubService = githubService;
        this.projects = projects;
        this.deployments = deployments;
    }

    public record DeployRequest(Boolean githubRepo, String customDomain) {}

    @PostMapping("/projects/{id}/deploy")
    @Transactional
    public ResponseEntity<?> deploy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestBody DeployRequest req) {
        if (!hasScope(jwt, "builder.project:deploy")) return ResponseEntity.status(403).build();
        var project = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        String githubRepo = null;
        String githubRepoUrl = null;
        if (Boolean.TRUE.equals(req.githubRepo())) {
            try {
                Map<String, String> repo = githubService.createRepository("builder-" + project.getName().toLowerCase().replace(" ", "-"), false);
                githubRepo = repo.get("name");
                githubRepoUrl = repo.get("cloneUrl");
            } catch (Exception e) {
                return ResponseEntity.internalServerError().body(Map.of("error", "Failed to create GitHub repo: " + e.getMessage()));
            }
        }
        Deployment d = deploymentService.createDeployment(project, githubRepo, githubRepoUrl, req.customDomain());
        // Trigger async deployment
        deploymentService.deployAsync(d, project);
        String url = deploymentService.getDeploymentUrl(d);
        return ResponseEntity.ok(Map.of(
                "deploymentId", d.getId().toString(),
                "url", url,
                "status", d.getStatus(),
                "subdomain", d.getSubdomain() == null ? "" : d.getSubdomain(),
                "githubRepo", githubRepo == null ? "" : githubRepo,
                "githubRepoUrl", githubRepoUrl == null ? "" : githubRepoUrl
        ));
    }

    @GetMapping("/projects/{id}/deployments")
    public ResponseEntity<?> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var project = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        List<Deployment> list = deployments.findByProjectId(id);
        return ResponseEntity.ok(list.stream().map(d -> Map.of(
                "id", d.getId().toString(),
                "status", d.getStatus(),
                "url", deploymentService.getDeploymentUrl(d),
                "githubRepo", d.getGithubRepo() == null ? "" : d.getGithubRepo(),
                "deployedAt", d.getDeployedAt() == null ? null : d.getDeployedAt().toString()
        )).toList());
    }

    @PostMapping("/deployments/{id}/redeploy")
    @Transactional
    public ResponseEntity<?> redeploy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var d = deployments.findById(id).orElseThrow();
        var project = projects.findById(d.getProjectId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        // Trigger async redeployment
        deploymentService.deployAsync(d, project);
        return ResponseEntity.ok(Map.of("status", "redeploying", "deploymentId", d.getId().toString()));
    }

    @DeleteMapping("/deployments/{id}")
    @Transactional
    public ResponseEntity<?> undeploy(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        var d = deployments.findById(id).orElseThrow();
        var project = projects.findById(d.getProjectId()).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        deployments.delete(d);
        return ResponseEntity.noContent().build();
    }
}

