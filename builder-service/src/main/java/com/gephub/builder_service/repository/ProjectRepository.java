package com.gephub.builder_service.repository;

import com.gephub.builder_service.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByOrganizationId(UUID organizationId);
    List<Project> findByOrganizationIdAndStatus(UUID organizationId, String status);
}

