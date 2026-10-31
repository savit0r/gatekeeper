package com.gatekeeper.apikey.service;

import com.gatekeeper.apikey.crypto.ApiKeyCrypto;
import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.domain.ApiKeyStatus;
import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.ApiKeyResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.exception.ApiKeyNotFoundException;
import com.gatekeeper.apikey.repository.ApiKeyRepository;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.exception.DeveloperNotFoundException;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.exception.UsagePlanNotFoundException;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final DeveloperRepository developerRepository;
    private final UsagePlanRepository usagePlanRepository;
    private final ApiKeyCrypto apiKeyCrypto;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         DeveloperRepository developerRepository,
                         UsagePlanRepository usagePlanRepository,
                         ApiKeyCrypto apiKeyCrypto) {
        this.apiKeyRepository = apiKeyRepository;
        this.developerRepository = developerRepository;
        this.usagePlanRepository = usagePlanRepository;
        this.apiKeyCrypto = apiKeyCrypto;
    }

    public ApiKeyCreatedResponse createApiKey(UUID developerId, CreateApiKeyRequest request) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new DeveloperNotFoundException(developerId));

        UsagePlan plan = usagePlanRepository.findById(request.planId())
                .orElseThrow(() -> new UsagePlanNotFoundException(request.planId()));

        ApiKeyCrypto.GeneratedKey generatedKey = apiKeyCrypto.generateKey();

        ApiKey apiKey = new ApiKey(
                UUID.randomUUID(),
                developer,
                plan,
                request.name(),
                generatedKey.keyPrefix(),
                generatedKey.keyHash(),
                request.expiresAt()
        );

        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponse(
                saved.getId(),
                saved.getName(),
                generatedKey.rawKey(),
                saved.getKeyPrefix(),
                plan.getId(),
                plan.getName(),
                saved.getStatus(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(UUID developerId) {
        if (!developerRepository.existsById(developerId)) {
            throw new DeveloperNotFoundException(developerId);
        }
        return apiKeyRepository.findByDeveloperId(developerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiKeyResponse getApiKey(UUID developerId, UUID keyId) {
        ApiKey apiKey = findApiKeyForDeveloper(developerId, keyId);
        return toResponse(apiKey);
    }

    public void revokeApiKey(UUID developerId, UUID keyId) {
        ApiKey apiKey = findApiKeyForDeveloper(developerId, keyId);
        apiKey.revoke();
        apiKeyRepository.save(apiKey);
    }

    public ApiKeyCreatedResponse rotateApiKey(UUID developerId, UUID keyId) {
        ApiKey oldKey = findApiKeyForDeveloper(developerId, keyId);
        oldKey.revoke();
        apiKeyRepository.save(oldKey);

        ApiKeyCrypto.GeneratedKey generatedKey = apiKeyCrypto.generateKey();
        ApiKey newKey = new ApiKey(
                UUID.randomUUID(),
                oldKey.getDeveloper(),
                oldKey.getPlan(),
                oldKey.getName() + " (Rotated)",
                generatedKey.keyPrefix(),
                generatedKey.keyHash(),
                oldKey.getExpiresAt()
        );

        ApiKey saved = apiKeyRepository.save(newKey);

        return new ApiKeyCreatedResponse(
                saved.getId(),
                saved.getName(),
                generatedKey.rawKey(),
                saved.getKeyPrefix(),
                newKey.getPlan().getId(),
                newKey.getPlan().getName(),
                saved.getStatus(),
                saved.getExpiresAt(),
                saved.getCreatedAt()
        );
    }

    /**
     * Validates an incoming raw API key:
     * 1. Extracts the prefix
     * 2. Finds candidate active keys by prefix
     * 3. Performs constant-time comparison against the SHA-256 hash
     * 4. Validates expiration
     */
    public Optional<ApiKey> validateAndGetApiKey(String rawKey) {
        String prefix = apiKeyCrypto.extractPrefix(rawKey);
        if (prefix == null) {
            return Optional.empty();
        }

        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndStatus(prefix, ApiKeyStatus.ACTIVE);
        for (ApiKey candidate : candidates) {
            if (apiKeyCrypto.verifyKey(rawKey, candidate.getKeyHash())) {
                if (candidate.getExpiresAt() != null && Instant.now().isAfter(candidate.getExpiresAt())) {
                    candidate.setStatus(ApiKeyStatus.EXPIRED);
                    apiKeyRepository.save(candidate);
                    return Optional.empty();
                }
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    private ApiKey findApiKeyForDeveloper(UUID developerId, UUID keyId) {
        ApiKey apiKey = apiKeyRepository.findByIdWithDetails(keyId)
                .orElseThrow(() -> new ApiKeyNotFoundException(keyId));

        if (!apiKey.getDeveloper().getId().equals(developerId)) {
            throw new ApiKeyNotFoundException(keyId);
        }
        return apiKey;
    }

    private ApiKeyResponse toResponse(ApiKey key) {
        return new ApiKeyResponse(
                key.getId(),
                key.getName(),
                key.getKeyPrefix(),
                key.getPlan().getId(),
                key.getPlan().getName(),
                key.getStatus(),
                key.getExpiresAt(),
                key.getCreatedAt(),
                key.getRevokedAt()
        );
    }
}
