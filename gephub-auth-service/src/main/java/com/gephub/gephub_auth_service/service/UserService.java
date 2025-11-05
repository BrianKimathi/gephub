package com.gephub.gephub_auth_service.service;

import com.gephub.gephub_auth_service.domain.Organization;
import com.gephub.gephub_auth_service.domain.Membership;
import com.gephub.gephub_auth_service.domain.OrganizationRole;
import com.gephub.gephub_auth_service.domain.User;
import com.gephub.gephub_auth_service.repository.OrganizationRepository;
import com.gephub.gephub_auth_service.repository.UserRepository;
import com.gephub.gephub_auth_service.repository.MembershipRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipRepository membershipRepository;

    public UserService(UserRepository userRepository, OrganizationRepository organizationRepository, PasswordEncoder passwordEncoder, MembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.passwordEncoder = passwordEncoder;
        this.membershipRepository = membershipRepository;
    }

    public User register(String email, String rawPassword, String organizationName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email.toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        userRepository.save(user);

        if (organizationName != null && !organizationName.isBlank()) {
            Organization org = new Organization();
            org.setId(UUID.randomUUID());
            org.setName(organizationName);
            organizationRepository.save(org);
            Membership m = new Membership();
            m.setId(new Membership.MembershipId(user.getId(), org.getId()));
            m.setRole(OrganizationRole.OWNER);
            membershipRepository.save(m);
        }
        return user;
    }

    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(email.toLowerCase()).orElseThrow();
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("User not active");
        }
        return user;
    }
}


