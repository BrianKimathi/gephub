package com.gephub.gephub_auth_service.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class Membership {
    @EmbeddedId
    private MembershipId id;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrganizationRole role;

    public MembershipId getId() { return id; }
    public void setId(MembershipId id) { this.id = id; }
    public OrganizationRole getRole() { return role; }
    public void setRole(OrganizationRole role) { this.role = role; }

    @Embeddable
    public static class MembershipId implements Serializable {
        @Column(name = "user_id")
        private UUID userId;
        @Column(name = "organization_id")
        private UUID organizationId;
        public MembershipId() {}
        public MembershipId(UUID userId, UUID organizationId) { this.userId = userId; this.organizationId = organizationId; }
        public UUID getUserId() { return userId; }
        public void setUserId(UUID userId) { this.userId = userId; }
        public UUID getOrganizationId() { return organizationId; }
        public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    }
}


