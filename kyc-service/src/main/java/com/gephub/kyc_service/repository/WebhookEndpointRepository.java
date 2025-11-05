package com.gephub.kyc_service.repository;

import com.gephub.kyc_service.domain.WebhookEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {
    List<WebhookEndpoint> findByOrganizationIdAndActiveTrue(UUID organizationId);
}


