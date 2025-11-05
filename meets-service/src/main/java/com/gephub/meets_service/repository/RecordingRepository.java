package com.gephub.meets_service.repository;

import com.gephub.meets_service.domain.Recording;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecordingRepository extends JpaRepository<Recording, UUID> {
    List<Recording> findByRoomId(UUID roomId);
    Optional<Recording> findByRoomIdAndStatus(UUID roomId, String status);
}

