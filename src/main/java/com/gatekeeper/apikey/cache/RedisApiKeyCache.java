package com.gatekeeper.apikey.cache;

import com.gatekeeper.apikey.domain.ApiKeyStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class RedisApiKeyCache implements ApiKeyCache {

    private static final Logger log = LoggerFactory.getLogger(RedisApiKeyCache.class);
    public static final String KEY_PREFIX = "gatekeeper:keycache:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisApiKeyCache(
            StringRedisTemplate redisTemplate,
            @Value("${gatekeeper.cache.api-key-ttl-seconds:600}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public Optional<CachedApiKey> get(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return Optional.empty();
        }

        try {
            String redisKey = KEY_PREFIX + keyHash;
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisKey);
            if (entries == null || entries.isEmpty()) {
                return Optional.empty();
            }

            String idStr = (String) entries.get("id");
            String name = (String) entries.get("name");
            String keyPrefix = (String) entries.get("keyPrefix");
            String hash = (String) entries.get("keyHash");
            String devIdStr = (String) entries.get("developerId");
            String planIdStr = (String) entries.get("planId");
            String planName = (String) entries.get("planName");
            String reqPerWindowStr = (String) entries.get("requestsPerWindow");
            String windowSecStr = (String) entries.get("windowSeconds");
            String statusStr = (String) entries.get("status");
            String expiresAtStr = (String) entries.get("expiresAt");

            if (idStr == null || planIdStr == null || reqPerWindowStr == null || windowSecStr == null) {
                return Optional.empty();
            }

            CachedApiKey cached = new CachedApiKey(
                    UUID.fromString(idStr),
                    name,
                    keyPrefix,
                    hash,
                    devIdStr != null ? UUID.fromString(devIdStr) : UUID.randomUUID(),
                    UUID.fromString(planIdStr),
                    planName,
                    Integer.parseInt(reqPerWindowStr),
                    Integer.parseInt(windowSecStr),
                    statusStr != null ? ApiKeyStatus.valueOf(statusStr) : ApiKeyStatus.ACTIVE,
                    expiresAtStr != null && !expiresAtStr.isBlank() ? Instant.parse(expiresAtStr) : null
            );

            return Optional.of(cached);
        } catch (Exception e) {
            log.warn("Redis key cache get failure for hash [{}]: {}. Falling back to database.", keyHash, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String keyHash, CachedApiKey cachedKey) {
        if (keyHash == null || cachedKey == null) {
            return;
        }

        try {
            String redisKey = KEY_PREFIX + keyHash;
            Map<String, String> values = new HashMap<>();
            values.put("id", cachedKey.id().toString());
            values.put("name", cachedKey.name() != null ? cachedKey.name() : "");
            values.put("keyPrefix", cachedKey.keyPrefix() != null ? cachedKey.keyPrefix() : "");
            values.put("keyHash", cachedKey.keyHash() != null ? cachedKey.keyHash() : "");
            values.put("developerId", cachedKey.developerId() != null ? cachedKey.developerId().toString() : "");
            values.put("planId", cachedKey.planId().toString());
            values.put("planName", cachedKey.planName() != null ? cachedKey.planName() : "");
            values.put("requestsPerWindow", String.valueOf(cachedKey.requestsPerWindow()));
            values.put("windowSeconds", String.valueOf(cachedKey.windowSeconds()));
            values.put("status", cachedKey.status() != null ? cachedKey.status().name() : ApiKeyStatus.ACTIVE.name());
            if (cachedKey.expiresAt() != null) {
                values.put("expiresAt", cachedKey.expiresAt().toString());
            }

            redisTemplate.opsForHash().putAll(redisKey, values);
            redisTemplate.expire(redisKey, ttl);
        } catch (Exception e) {
            log.warn("Redis key cache put failure for hash [{}]: {}", keyHash, e.getMessage());
        }
    }

    @Override
    public void evict(String keyHash) {
        if (keyHash == null || keyHash.isBlank()) {
            return;
        }

        try {
            String redisKey = KEY_PREFIX + keyHash;
            redisTemplate.delete(redisKey);
        } catch (Exception e) {
            log.warn("Redis key cache evict failure for hash [{}]: {}", keyHash, e.getMessage());
        }
    }
}
