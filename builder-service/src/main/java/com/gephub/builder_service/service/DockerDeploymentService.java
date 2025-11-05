package com.gephub.builder_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DockerDeploymentService {
    private final String deploymentRoot;
    private final String dockerRegistry;
    private final boolean enabled;

    public DockerDeploymentService(@Value("${gephub.builder.deployment.docker.enabled:false}") boolean enabled,
                                   @Value("${gephub.builder.deployment.docker.registry:}") String dockerRegistry,
                                   @Value("${gephub.builder.storage.root}") String storageRoot) {
        this.enabled = enabled;
        this.dockerRegistry = dockerRegistry;
        this.deploymentRoot = Paths.get(storageRoot, "deployments").toString();
    }

    public void buildAndDeploy(UUID deploymentId, String githubRepoUrl, String subdomain) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("Docker deployment is not enabled");
        }

        Path workDir = Paths.get(deploymentRoot, deploymentId.toString());
        Files.createDirectories(workDir);

        try {
            // Step 1: Clone repository
            executeCommand(workDir, "git", "clone", githubRepoUrl, ".");

            // Step 2: Create Dockerfile if it doesn't exist
            Path dockerfile = workDir.resolve("Dockerfile");
            if (!Files.exists(dockerfile)) {
                createDefaultDockerfile(dockerfile);
            }

            // Step 3: Build Docker image
            String imageName = dockerRegistry.isEmpty() ? 
                "gephub/" + subdomain.toLowerCase() + ":latest" :
                dockerRegistry + "/" + subdomain.toLowerCase() + ":latest";
            executeCommand(workDir, "docker", "build", "-t", imageName, ".");

            // Step 4: Push to registry (if configured)
            if (!dockerRegistry.isEmpty()) {
                executeCommand(workDir, "docker", "push", imageName);
            }

            // Step 5: Deploy (using docker-compose or kubectl)
            // This would typically involve:
            // - Creating docker-compose.yml
            // - Running docker-compose up
            // - Or: kubectl apply -f deployment.yaml

        } finally {
            // Cleanup work directory (optional, keep for debugging)
            // deleteDirectory(workDir.toFile());
        }
    }

    private void createDefaultDockerfile(Path dockerfile) throws Exception {
        String content = """
            FROM node:20-alpine AS builder
            WORKDIR /app
            COPY package*.json ./
            RUN npm ci
            COPY . .
            RUN npm run build

            FROM node:20-alpine
            WORKDIR /app
            COPY --from=builder /app/.next ./.next
            COPY --from=builder /app/public ./public
            COPY --from=builder /app/package*.json ./
            COPY --from=builder /app/node_modules ./node_modules
            EXPOSE 3000
            CMD ["npm", "start"]
            """;
        Files.writeString(dockerfile, content);
    }

    private void executeCommand(Path workDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();

        List<String> output = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.add(line);
                System.out.println(line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Command failed with exit code " + exitCode + ": " + String.join("\n", output));
        }
    }
}

