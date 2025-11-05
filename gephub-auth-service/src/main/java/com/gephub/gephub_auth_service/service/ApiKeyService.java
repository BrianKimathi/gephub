package com.gephub.gephub_auth_service.service;

import com.gephub.gephub_auth_service.domain.ApiKey;
import com.gephub.gephub_auth_service.domain.Organization;
import com.gephub.gephub_auth_service.domain.Product;
import com.gephub.gephub_auth_service.repository.ApiKeyRepository;
import com.gephub.gephub_auth_service.repository.ProductRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApiKeyService {
    private final ApiKeyRepository apiKeyRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public record GeneratedKey(String prefix, String secret, UUID id) {}

    public ApiKeyService(ApiKeyRepository apiKeyRepository, ProductRepository productRepository, PasswordEncoder passwordEncoder) {
        this.apiKeyRepository = apiKeyRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public GeneratedKey createKey(Organization organization, UUID createdByUserId, String environment, List<String> productCodes) {
        String secret = generateSecret();
        String prefix = generatePrefix(environment);

        ApiKey apiKey = new ApiKey();
        apiKey.setId(UUID.randomUUID());
        apiKey.setOrganization(organization);
        apiKey.setCreatedByUserId(createdByUserId);
        apiKey.setEnvironment(environment);
        apiKey.setKeyPrefix(prefix);
        apiKey.setSecretHash(passwordEncoder.encode(secret));
        Set<Product> products = productCodes.stream()
            .map(code -> productRepository.findByCode(code).orElseThrow(() -> new IllegalArgumentException("Unknown product: " + code)))
            .collect(Collectors.toSet());
        apiKey.setProducts(products);

        apiKeyRepository.save(apiKey);
        return new GeneratedKey(prefix, renderDisplaySecret(environment, prefix, secret), apiKey.getId());
    }

    @Transactional
    public void revoke(UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId).orElseThrow();
        apiKey.setStatus("revoked");
        apiKeyRepository.save(apiKey);
    }

    @Transactional
    public GeneratedKey rotate(UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId).orElseThrow();
        String newSecret = generateSecret();
        String newPrefix = generatePrefix(apiKey.getEnvironment());
        apiKey.setKeyPrefix(newPrefix);
        apiKey.setSecretHash(passwordEncoder.encode(newSecret));
        apiKeyRepository.save(apiKey);
        return new GeneratedKey(newPrefix, renderDisplaySecret(apiKey.getEnvironment(), newPrefix, newSecret), apiKey.getId());
    }

    public List<ApiKey> listByOrganization(UUID orgId) {
        return apiKeyRepository.findByOrganization_Id(orgId);
    }

    public boolean verifyPresentedKey(String presented) {
        ParsedKey parsed = parsePresentedKey(presented);
        ApiKey apiKey = apiKeyRepository.findByKeyPrefix(parsed.prefix()).orElse(null);
        if (apiKey == null || !"active".equals(apiKey.getStatus())) return false;
        return passwordEncoder.matches(parsed.secret(), apiKey.getSecretHash());
    }

    private String generatePrefix(String environment) {
        String env = "live".equalsIgnoreCase(environment) ? "live" : "test";
        byte[] rnd = new byte[6];
        secureRandom.nextBytes(rnd);
        return "gpk_" + env + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
    }

    private String generateSecret() {
        byte[] rnd = new byte[32];
        secureRandom.nextBytes(rnd);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(rnd);
    }

    private String renderDisplaySecret(String environment, String prefix, String secret) {
        return prefix + "." + secret;
    }

    public record ParsedKey(String prefix, String secret) {}

    public ParsedKey parsePresentedKey(String presented) {
        int i = presented.indexOf('.')
            ;
        if (i <= 0) throw new IllegalArgumentException("Invalid API key format");
        return new ParsedKey(presented.substring(0, i), presented.substring(i + 1));
    }

    public ApiKey verifyAndLoad(String presented) {
        ParsedKey parsed = parsePresentedKey(presented);
        ApiKey apiKey = apiKeyRepository.findByKeyPrefix(parsed.prefix()).orElseThrow(() -> new IllegalArgumentException("Unknown API key"));
        if (!"active".equals(apiKey.getStatus())) throw new IllegalStateException("API key not active");
        if (!passwordEncoder.matches(parsed.secret(), apiKey.getSecretHash())) throw new IllegalArgumentException("Invalid API key secret");
        return apiKey;
    }
}


