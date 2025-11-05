package com.gephub.builder_service.repository;

import com.gephub.builder_service.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<Page, UUID> {
    List<Page> findByProjectId(UUID projectId);
    Optional<Page> findByProjectIdAndPath(UUID projectId, String path);
}

