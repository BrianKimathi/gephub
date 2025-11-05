package com.gephub.meets_service.repository;

import com.gephub.meets_service.domain.Participant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    List<Participant> findByRoomIdAndLeftAtIsNull(UUID roomId);
    long countByRoomIdAndLeftAtIsNull(UUID roomId);
}

