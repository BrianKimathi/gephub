package com.gephub.builder_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gephub.builder_service.domain.Project;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackendGeneratorService {
    private final ObjectMapper json = new ObjectMapper();

    public Resource generateSpringBootProject(Project project, List<String> features) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(baos)) {
            String artifactId = project.getName().toLowerCase().replace(" ", "-");
            addEntry(zos, "pom.xml", generatePomXml(artifactId, features));
            addEntry(zos, "src/main/java/com/example/Application.java", generateMainClass(artifactId));
            addEntry(zos, "src/main/resources/application.yml", generateApplicationYml());
            addEntry(zos, "Dockerfile", generateDockerfile());
            if (features.contains("api")) {
                addEntry(zos, "src/main/java/com/example/controller/ExampleController.java", generateController());
            }
            if (features.contains("database")) {
                addEntry(zos, "src/main/java/com/example/domain/Example.java", generateEntity());
                addEntry(zos, "src/main/java/com/example/repository/ExampleRepository.java", generateRepository());
            }
            if (features.contains("auth")) {
                addEntry(zos, "src/main/java/com/example/config/SecurityConfig.java", generateSecurityConfig());
            }
        }
        return new ByteArrayResource(baos.toByteArray());
    }

    private String generatePomXml(String artifactId, List<String> features) {
        StringBuilder deps = new StringBuilder();
        deps.append("        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-web</artifactId>\n        </dependency>\n");
        if (features.contains("database")) {
            deps.append("        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-data-jpa</artifactId>\n        </dependency>\n");
            deps.append("        <dependency>\n            <groupId>org.postgresql</groupId>\n            <artifactId>postgresql</artifactId>\n        </dependency>\n");
        }
        if (features.contains("auth")) {
            deps.append("        <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-security</artifactId>\n        </dependency>\n");
        }
        return "<?xml version=\"1.0\"?>\n<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n    <modelVersion>4.0.0</modelVersion>\n    <parent>\n        <groupId>org.springframework.boot</groupId>\n        <artifactId>spring-boot-starter-parent</artifactId>\n        <version>3.5.7</version>\n    </parent>\n    <groupId>com.example</groupId>\n    <artifactId>" + artifactId + "</artifactId>\n    <version>1.0.0</version>\n    <dependencies>\n" + deps + "    </dependencies>\n</project>\n";
    }

    private String generateMainClass(String artifactId) {
        return "package com.example;\n\nimport org.springframework.boot.SpringApplication;\nimport org.springframework.boot.autoconfigure.SpringBootApplication;\n\n@SpringBootApplication\npublic class Application {\n    public static void main(String[] args) {\n        SpringApplication.run(Application.class, args);\n    }\n}\n";
    }

    private String generateApplicationYml() {
        return "spring:\n  datasource:\n    url: jdbc:postgresql://localhost:5432/mydb\n    username: user\n    password: pass\nserver:\n  port: 8080\n";
    }

    private String generateDockerfile() {
        return "FROM eclipse-temurin:21-jre\nWORKDIR /app\nCOPY target/*.jar app.jar\nENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n";
    }

    private String generateController() {
        return "package com.example.controller;\n\nimport org.springframework.web.bind.annotation.*;\nimport java.util.Map;\n\n@RestController\n@RequestMapping(\"/api\")\npublic class ExampleController {\n    @GetMapping\n    public Map<String, String> hello() {\n        return Map.of(\"message\", \"Hello World\");\n    }\n}\n";
    }

    private String generateEntity() {
        return "package com.example.domain;\n\nimport jakarta.persistence.*;\nimport java.util.UUID;\n\n@Entity\npublic class Example {\n    @Id\n    @GeneratedValue\n    private UUID id;\n    private String name;\n    // Getters and setters\n}\n";
    }

    private String generateRepository() {
        return "package com.example.repository;\n\nimport com.example.domain.Example;\nimport org.springframework.data.jpa.repository.JpaRepository;\nimport java.util.UUID;\n\npublic interface ExampleRepository extends JpaRepository<Example, UUID> {\n}\n";
    }

    private String generateSecurityConfig() {
        return "package com.example.config;\n\nimport org.springframework.context.annotation.Bean;\nimport org.springframework.context.annotation.Configuration;\nimport org.springframework.security.config.annotation.web.builders.HttpSecurity;\nimport org.springframework.security.web.SecurityFilterChain;\n\n@Configuration\npublic class SecurityConfig {\n    @Bean\n    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {\n        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());\n        return http.build();\n    }\n}\n";
    }

    private void addEntry(ZipArchiveOutputStream zos, String name, String content) throws Exception {
        ZipArchiveEntry entry = new ZipArchiveEntry(name);
        zos.putArchiveEntry(entry);
        zos.write(content.getBytes());
        zos.closeArchiveEntry();
    }

    public Map<String, Object> parseEndpoints(List<String> features) {
        Map<String, Object> endpoints = new HashMap<>();
        if (features.contains("api")) {
            endpoints.put("endpoints", List.of(
                    Map.of("method", "GET", "path", "/api", "description", "Hello endpoint")
            ));
        }
        return endpoints;
    }
}

