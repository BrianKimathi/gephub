package com.gephub.gephub_auth_service.repository;

import com.gephub.gephub_auth_service.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    Optional<ApiKey> findByKeyPrefix(String keyPrefix);
    List<ApiKey> findByOrganization_Id(UUID organizationId);
}


