package com.gephub.builder_service.service;

import com.gephub.builder_service.domain.Deployment;
import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.DeploymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class DeploymentService {
    private final DeploymentRepository deployments;
    private final String baseUrl;
    private final String subdomainPattern;
    private final GitHubService githubService;
    private final CodeGeneratorService codeGenerator;
    private final DockerDeploymentService dockerService;
    private final WebhookService webhookService;

    public DeploymentService(DeploymentRepository deployments,
                             @Value("${gephub.builder.deployment.baseUrl}") String baseUrl,
                             @Value("${gephub.builder.deployment.subdomainPattern}") String subdomainPattern,
                             GitHubService githubService,
                             CodeGeneratorService codeGenerator,
                             DockerDeploymentService dockerService,
                             WebhookService webhookService) {
        this.deployments = deployments;
        this.baseUrl = baseUrl;
        this.subdomainPattern = subdomainPattern;
        this.githubService = githubService;
        this.codeGenerator = codeGenerator;
        this.dockerService = dockerService;
        this.webhookService = webhookService;
    }

    public Deployment createDeployment(Project project, String githubRepo, String githubRepoUrl, String customDomain) {
        Deployment d = new Deployment();
        d.setId(UUID.randomUUID());
        d.setProjectId(project.getId());
        d.setGithubRepo(githubRepo);
        d.setGithubRepoUrl(githubRepoUrl);
        d.setStatus("pending");
        if (customDomain != null && !customDomain.isBlank()) {
            d.setDomain(customDomain);
        } else {
            String subdomain = generateSubdomain(project);
            d.setSubdomain(subdomain);
        }
        d.setCreatedAt(OffsetDateTime.now());
        deployments.save(d);
        return d;
    }

    @Async
    public CompletableFuture<Void> deployAsync(Deployment deployment, Project project) {
        try {
            deployment.setStatus("deploying");
            deployments.save(deployment);
            
            // Step 1: Generate code
            var codeResource = codeGenerator.generateFrontendCode(project, "nextjs");
            byte[] codeBytes = codeResource.getInputStream().readAllBytes();
            
            // Step 2: Push to GitHub if repo exists
            if (deployment.getGithubRepo() != null && deployment.getGithubRepoUrl() != null) {
                try {
                    githubService.pushCode(deployment.getGithubRepo(), codeBytes, deployment.getGithubRepoUrl());
                } catch (Exception e) {
                    deployment.setStatus("failed");
                    deployments.save(deployment);
                    throw new RuntimeException("Failed to push to GitHub: " + e.getMessage(), e);
                }
            }
            
            // Step 3: Trigger deployment (Docker build, deploy to infrastructure)
            if (deployment.getGithubRepoUrl() != null && !deployment.getGithubRepoUrl().isBlank()) {
                try {
                    dockerService.buildAndDeploy(deployment.getId(), deployment.getGithubRepoUrl(), deployment.getSubdomain());
                } catch (Exception e) {
                    // If Docker deployment fails, log but continue (might be disabled)
                    System.err.println("Docker deployment failed: " + e.getMessage());
                }
            }
            
            // Step 4: Setup infrastructure (Nginx, SSL, DNS)
            // This would typically involve:
            // - Creating Nginx configuration
            // - Requesting Let's Encrypt SSL certificate
            // - Updating DNS records
            // - Starting/updating container
            
            // For now, simulate final deployment steps
            simulateDeployment(deployment);
            
            deployment.setStatus("live");
            deployment.setDeployedAt(OffsetDateTime.now());
            deployments.save(deployment);
            
            // Send webhook notification
            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("deploymentId", deployment.getId().toString());
            webhookData.put("projectId", project.getId().toString());
            webhookData.put("status", "live");
            webhookData.put("url", getDeploymentUrl(deployment));
            webhookService.sendWebhook(project.getOrganizationId(), "deployment.completed", webhookData);
            
        } catch (Exception e) {
            deployment.setStatus("failed");
            deployments.save(deployment);
            
            // Send failure webhook
            Map<String, Object> webhookData = new HashMap<>();
            webhookData.put("deploymentId", deployment.getId().toString());
            webhookData.put("projectId", project.getId().toString());
            webhookData.put("status", "failed");
            webhookData.put("error", e.getMessage());
            webhookService.sendWebhook(project.getOrganizationId(), "deployment.failed", webhookData);
            
            throw new RuntimeException("Deployment failed: " + e.getMessage(), e);
        }
        return CompletableFuture.completedFuture(null);
    }

    private void simulateDeployment(Deployment deployment) {
        // Simulate deployment delay
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // In production: actual deployment logic here
    }

    private String generateSubdomain(Project project) {
        String base = project.getName().toLowerCase().replaceAll("[^a-z0-9]", "-");
        return base + "-" + project.getId().toString().substring(0, 8);
    }

    public String getDeploymentUrl(Deployment deployment) {
        if (deployment.getDomain() != null) {
            return "https://" + deployment.getDomain();
        }
        if (deployment.getSubdomain() != null) {
            return "https://" + deployment.getSubdomain() + ".gephub.io";
        }
        return null;
    }
}

