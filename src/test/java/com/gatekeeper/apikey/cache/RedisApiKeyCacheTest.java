package com.gatekeeper.apikey.cache;

import com.gatekeeper.apikey.domain.ApiKeyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisApiKeyCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisApiKeyCache cache;

    @BeforeEach
    void setUp() {
        cache = new RedisApiKeyCache(redisTemplate, 300);
    }

    @Test
    @DisplayName("get returns CachedApiKey when hash exists in Redis")
    void get_returnsCachedKey_whenExists() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        UUID keyId = UUID.randomUUID();
        UUID devId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();

        Map<Object, Object> entries = Map.of(
                "id", keyId.toString(),
                "name", "Test Key",
                "keyPrefix", "gk_live_12345678",
                "keyHash", "hash123",
                "developerId", devId.toString(),
                "planId", planId.toString(),
                "planName", "Pro",
                "requestsPerWindow", "100",
                "windowSeconds", "60",
                "status", "ACTIVE"
        );

        when(hashOperations.entries("gatekeeper:keycache:hash123")).thenReturn(entries);

        Optional<CachedApiKey> result = cache.get("hash123");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(keyId);
        assertThat(result.get().name()).isEqualTo("Test Key");
        assertThat(result.get().planName()).isEqualTo("Pro");
        assertThat(result.get().requestsPerWindow()).isEqualTo(100);
        assertThat(result.get().windowSeconds()).isEqualTo(60);
        assertThat(result.get().status()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("get returns empty when hash does not exist in Redis")
    void get_returnsEmpty_whenNotExists() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("gatekeeper:keycache:missing")).thenReturn(Map.of());

        Optional<CachedApiKey> result = cache.get("missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("get returns empty gracefully when Redis throws an exception (fail-open)")
    void get_returnsEmpty_onRedisException() {
        when(redisTemplate.opsForHash()).thenThrow(new RedisConnectionFailureException("Redis is down"));

        Optional<CachedApiKey> result = cache.get("somehash");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("put stores hash and sets TTL")
    void put_storesHashAndSetsTTL() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        CachedApiKey toCache = new CachedApiKey(
                UUID.randomUUID(),
                "My Key",
                "gk_live_abcd1234",
                "hashxyz",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Starter",
                20,
                60,
                ApiKeyStatus.ACTIVE,
                Instant.now().plusSeconds(3600)
        );

        cache.put("hashxyz", toCache);

        verify(hashOperations).putAll(eq("gatekeeper:keycache:hashxyz"), anyMap());
        verify(redisTemplate).expire(eq("gatekeeper:keycache:hashxyz"), eq(Duration.ofSeconds(300)));
    }

    @Test
    @DisplayName("evict deletes the cache key from Redis")
    void evict_deletesKey() {
        cache.evict("hashxyz");

        verify(redisTemplate).delete("gatekeeper:keycache:hashxyz");
    }
}
