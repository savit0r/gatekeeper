package com.gatekeeper.apikey.cache;

import com.gatekeeper.apikey.crypto.ApiKeyCrypto;
import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApiKeyCacheIntegrationTest {

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyCrypto apiKeyCrypto;

    @Autowired
    private DeveloperRepository developerRepository;

    @Autowired
    private UsagePlanRepository usagePlanRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Developer developer;
    private UsagePlan plan;

    @BeforeEach
    void setUp() {
        var keys = redisTemplate.keys("gatekeeper:keycache:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        developer = developerRepository.save(new Developer(
                UUID.randomUUID(),
                "cache-test-" + UUID.randomUUID() + "@gatekeeper.dev",
                "hashed_pwd",
                "ROLE_DEVELOPER",
                Instant.now()
        ));

        plan = usagePlanRepository.save(new UsagePlan(
                UUID.randomUUID(),
                "Cache Plan " + UUID.randomUUID(),
                250,
                60,
                Instant.now()
        ));
    }

    @Test
    @DisplayName("Should populate Redis cache on first validation, serve from cache on second, and evict on revoke")
    void shouldCacheAndEvictApiKeyLifecycle() {
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(
                developer.getId(),
                new CreateApiKeyRequest("Cache Validation Key", plan.getId(), null)
        );

        String rawKey = created.rawKey();
        String keyHash = apiKeyCrypto.hashKey(rawKey);
        String redisKey = RedisApiKeyCache.KEY_PREFIX + keyHash;

        // 1. Initial state: cache miss, nothing in Redis
        Map<Object, Object> initialEntries = redisTemplate.opsForHash().entries(redisKey);
        assertThat(initialEntries).isEmpty();

        // 2. First validation: queries DB and populates Redis cache
        Optional<ApiKey> firstCall = apiKeyService.validateAndGetApiKey(rawKey);
        assertThat(firstCall).isPresent();
        assertThat(firstCall.get().getId()).isEqualTo(created.id());

        // Verify Redis now contains the cached API key metadata
        Map<Object, Object> cachedEntries = redisTemplate.opsForHash().entries(redisKey);
        assertThat(cachedEntries).isNotEmpty();
        assertThat(cachedEntries.get("id")).isEqualTo(created.id().toString());
        assertThat(cachedEntries.get("name")).isEqualTo("Cache Validation Key");
        assertThat(cachedEntries.get("planName")).isEqualTo(plan.getName());
        assertThat(cachedEntries.get("requestsPerWindow")).isEqualTo("250");
        assertThat(cachedEntries.get("windowSeconds")).isEqualTo("60");

        // 3. Second validation: serves directly from Redis cache
        Optional<ApiKey> secondCall = apiKeyService.validateAndGetApiKey(rawKey);
        assertThat(secondCall).isPresent();
        assertThat(secondCall.get().getId()).isEqualTo(created.id());
        assertThat(secondCall.get().getPlan().getRequestsPerWindow()).isEqualTo(250);

        // 4. Revocation: evicts entry from Redis immediately
        apiKeyService.revokeApiKey(developer.getId(), created.id());

        Map<Object, Object> postRevokeEntries = redisTemplate.opsForHash().entries(redisKey);
        assertThat(postRevokeEntries).isEmpty();

        // 5. Subsequent validation rejects revoked key
        Optional<ApiKey> thirdCall = apiKeyService.validateAndGetApiKey(rawKey);
        assertThat(thirdCall).isEmpty();
    }
}
