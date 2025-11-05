package com.gephub.builder_service.domain;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "generated_backends")
public class GeneratedBackend {
    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "backend_type", nullable = false)
    private String backendType = "spring-boot";
    @Column(name = "github_repo")
    private String githubRepo;
    @Column(name = "github_repo_url")
    private String githubRepoUrl;
    @Column(columnDefinition = "jsonb")
    private String endpoints;
    @Column(name = "database_schema", columnDefinition = "jsonb")
    private String databaseSchema;
    @Column(name = "deployed_at")
    private OffsetDateTime deployedAt;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    public String getBackendType() { return backendType; }
    public void setBackendType(String backendType) { this.backendType = backendType; }
    public String getGithubRepo() { return githubRepo; }
    public void setGithubRepo(String githubRepo) { this.githubRepo = githubRepo; }
    public String getGithubRepoUrl() { return githubRepoUrl; }
    public void setGithubRepoUrl(String githubRepoUrl) { this.githubRepoUrl = githubRepoUrl; }
    public String getEndpoints() { return endpoints; }
    public void setEndpoints(String endpoints) { this.endpoints = endpoints; }
    public String getDatabaseSchema() { return databaseSchema; }
    public void setDatabaseSchema(String databaseSchema) { this.databaseSchema = databaseSchema; }
    public OffsetDateTime getDeployedAt() { return deployedAt; }
    public void setDeployedAt(OffsetDateTime deployedAt) { this.deployedAt = deployedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}

