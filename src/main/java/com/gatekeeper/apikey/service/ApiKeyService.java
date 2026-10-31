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
import com.gatekeeper.apikey.cache.ApiKeyCache;
import com.gatekeeper.apikey.cache.CachedApiKey;
import com.gatekeeper.apikey.cache.NoOpApiKeyCache;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ApiKeyCache apiKeyCache;

    @Autowired
    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         DeveloperRepository developerRepository,
                         UsagePlanRepository usagePlanRepository,
                         ApiKeyCrypto apiKeyCrypto,
                         ApiKeyCache apiKeyCache) {
        this.apiKeyRepository = apiKeyRepository;
        this.developerRepository = developerRepository;
        this.usagePlanRepository = usagePlanRepository;
        this.apiKeyCrypto = apiKeyCrypto;
        this.apiKeyCache = apiKeyCache;
    }

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         DeveloperRepository developerRepository,
                         UsagePlanRepository usagePlanRepository,
                         ApiKeyCrypto apiKeyCrypto) {
        this(apiKeyRepository, developerRepository, usagePlanRepository, apiKeyCrypto, new NoOpApiKeyCache());
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
        apiKeyCache.evict(apiKey.getKeyHash());
    }

    public ApiKeyCreatedResponse rotateApiKey(UUID developerId, UUID keyId) {
        ApiKey oldKey = findApiKeyForDeveloper(developerId, keyId);
        oldKey.revoke();
        apiKeyRepository.save(oldKey);
        apiKeyCache.evict(oldKey.getKeyHash());

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
     * 2. Checks Redis cache first (avoids DB queries on hot paths)
     * 3. On cache miss, queries database by prefix and validates SHA-256 hash
     * 4. Validates expiration and populates Redis cache
     */
    public Optional<ApiKey> validateAndGetApiKey(String rawKey) {
        String prefix = apiKeyCrypto.extractPrefix(rawKey);
        if (prefix == null) {
            return Optional.empty();
        }

        String rawKeyHash = apiKeyCrypto.hashKey(rawKey);

        // 1. Check Redis cache first
        Optional<CachedApiKey> cachedOpt = apiKeyCache.get(rawKeyHash);
        if (cachedOpt.isPresent()) {
            CachedApiKey cached = cachedOpt.get();
            if (cached.isExpired() || cached.status() != ApiKeyStatus.ACTIVE) {
                apiKeyCache.evict(rawKeyHash);
                return Optional.empty();
            }
            UsagePlan plan = new UsagePlan(cached.planId(), cached.planName(), cached.requestsPerWindow(), cached.windowSeconds(), null);
            Developer dev = new Developer(cached.developerId(), "cached@gatekeeper.local", "cached", "ROLE_DEVELOPER", null);
            ApiKey apiKey = new ApiKey(cached.id(), dev, plan, cached.name(), cached.keyPrefix(), cached.keyHash(), cached.expiresAt());
            return Optional.of(apiKey);
        }

        // 2. Cache miss: DB query by prefix candidate
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndStatus(prefix, ApiKeyStatus.ACTIVE);
        for (ApiKey candidate : candidates) {
            if (apiKeyCrypto.verifyKey(rawKey, candidate.getKeyHash())) {
                if (candidate.getExpiresAt() != null && Instant.now().isAfter(candidate.getExpiresAt())) {
                    candidate.setStatus(ApiKeyStatus.EXPIRED);
                    apiKeyRepository.save(candidate);
                    apiKeyCache.evict(rawKeyHash);
                    return Optional.empty();
                }

                // Populate cache for future requests
                CachedApiKey toCache = new CachedApiKey(
                        candidate.getId(),
                        candidate.getName(),
                        candidate.getKeyPrefix(),
                        candidate.getKeyHash(),
                        candidate.getDeveloper().getId(),
                        candidate.getPlan().getId(),
                        candidate.getPlan().getName(),
                        candidate.getPlan().getRequestsPerWindow(),
                        candidate.getPlan().getWindowSeconds(),
                        candidate.getStatus(),
                        candidate.getExpiresAt()
                );
                apiKeyCache.put(rawKeyHash, toCache);

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
