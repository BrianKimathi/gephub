package com.gephub.kyc_service.repository;

import com.gephub.kyc_service.domain.KycResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface KycResultRepository extends JpaRepository<KycResult, UUID> {
    Optional<KycResult> findBySessionId(UUID sessionId);
}


