package com.gephub.gephub_auth_service.repository;

import com.gephub.gephub_auth_service.domain.Membership;
import com.gephub.gephub_auth_service.domain.OrganizationRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, Membership.MembershipId> {
    @Query("select m from Membership m where m.id.userId = :userId and m.id.organizationId = :orgId")
    Optional<Membership> find(@Param("userId") UUID userId, @Param("orgId") UUID orgId);

    @Query("select m from Membership m where m.id.userId = :userId")
    java.util.List<Membership> findByUser(@Param("userId") UUID userId);

    default boolean hasAtLeastRole(Optional<Membership> membership, OrganizationRole required) {
        if (membership.isEmpty()) return false;
        OrganizationRole have = membership.get().getRole();
        return rank(have) <= rank(required);
    }

    private int rank(OrganizationRole r) {
        return switch (r) {
            case OWNER -> 0;
            case ADMIN -> 1;
            case DEV -> 2;
            case READONLY -> 3;
        };
    }
}


