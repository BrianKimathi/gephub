package com.gephub.builder_service.web;

import com.gephub.builder_service.domain.Project;
import com.gephub.builder_service.repository.ProjectRepository;
import com.gephub.builder_service.service.CodeGeneratorService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static com.gephub.builder_service.security.AuthzUtil.*;

@RestController
@RequestMapping("/api/v1/builder")
public class ExportController {
    private final CodeGeneratorService codeGen;
    private final ProjectRepository projects;

    public ExportController(CodeGeneratorService codeGen, ProjectRepository projects) {
        this.codeGen = codeGen;
        this.projects = projects;
    }

    @PostMapping("/projects/{id}/export")
    public ResponseEntity<Resource> export(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam(defaultValue = "nextjs") String format) {
        var project = projects.findById(id).orElseThrow();
        UUID org = orgId(jwt);
        if (org != null && !org.equals(project.getOrganizationId())) return ResponseEntity.status(403).build();
        try {
            Resource zip = codeGen.generateFrontendCode(project, format);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + project.getName().toLowerCase().replace(" ", "-") + "-" + format + ".zip\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(zip);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}

