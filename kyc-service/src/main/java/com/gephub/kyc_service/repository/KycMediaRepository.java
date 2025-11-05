package com.gephub.kyc_service.repository;

import com.gephub.kyc_service.domain.KycMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycMediaRepository extends JpaRepository<KycMedia, UUID> {
    List<KycMedia> findBySessionId(UUID sessionId);
    boolean existsBySessionIdAndMediaType(UUID sessionId, String mediaType);
}


