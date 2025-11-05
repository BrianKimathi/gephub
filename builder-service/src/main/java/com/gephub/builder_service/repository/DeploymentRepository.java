package com.gephub.builder_service.repository;

import com.gephub.builder_service.domain.Deployment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {
    List<Deployment> findByProjectId(UUID projectId);
    Optional<Deployment> findByProjectIdAndStatus(UUID projectId, String status);
    Optional<Deployment> findBySubdomain(String subdomain);
}

