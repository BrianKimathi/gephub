package com.gephub.builder_service.repository;

import com.gephub.builder_service.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByOrganizationIdAndIsActive(UUID organizationId, Boolean isActive);
    List<WebhookEndpoint> findByOrganizationIdAndIsActiveAndEventTypesContaining(UUID organizationId, Boolean isActive, String eventType);
}

