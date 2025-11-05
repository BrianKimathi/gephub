package com.gephub.gephub_auth_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class JwtService {
    private final JwtEncoder encoder;

    @Value("${gephub.jwt.issuer:https://auth.gephub.local}")
    private String issuer;

    public JwtService(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String issueToken(String subject, Map<String, Object> claims, long ttlSeconds) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(subject);
        if (claims != null) {
            claims.forEach(builder::claim);
        }
        return encoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }
}


