package com.gephub.kyc_service.repository;

import com.gephub.kyc_service.domain.KycSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycSessionRepository extends JpaRepository<KycSession, UUID> {
    List<KycSession> findByOrganizationId(UUID organizationId);
}


