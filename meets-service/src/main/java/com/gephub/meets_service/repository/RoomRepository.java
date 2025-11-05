package com.gephub.meets_service.repository;

import com.gephub.meets_service.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByOrganizationIdAndCode(UUID organizationId, String code);
}

package com.gephub.meets_service.repository;

import com.gephub.meets_service.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByOrganizationIdAndCode(UUID organizationId, String code);
}


