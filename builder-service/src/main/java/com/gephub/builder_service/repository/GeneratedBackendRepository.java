package com.gephub.builder_service.repository;

import com.gephub.builder_service.domain.GeneratedBackend;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GeneratedBackendRepository extends JpaRepository<GeneratedBackend, UUID> {
    Optional<GeneratedBackend> findByProjectId(UUID projectId);
}

