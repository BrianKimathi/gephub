package com.gephub.gephub_auth_service.repository;

import com.gephub.gephub_auth_service.domain.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}


