package com.gatekeeper.gateway.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private static final String LUA_RATE_LIMIT_SCRIPT = """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            local ttl = redis.call('TTL', KEYS[1])
            local limit = tonumber(ARGV[2])
            local remaining = limit - current
            if remaining < 0 then
                remaining = 0
            end
            local allowed = 0
            if current <= limit then
                allowed = 1
            end
            return { allowed, current, remaining, ttl }
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> script;
    private final boolean failOpen;

    public RedisRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${gatekeeper.ratelimit.fail-open:false}") boolean failOpen) {
        this.redisTemplate = redisTemplate;
        this.failOpen = failOpen;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptText(LUA_RATE_LIMIT_SCRIPT);
        this.script.setResultType(List.class);
    }

    @Override
    public RateLimitResult checkRateLimit(UUID apiKeyId, int maxRequests, int windowSeconds) {
        if (apiKeyId == null || maxRequests <= 0 || windowSeconds <= 0) {
            throw new IllegalArgumentException("Invalid rate limit parameters");
        }

        long nowSeconds = Instant.now().getEpochSecond();
        long windowIndex = nowSeconds / windowSeconds;
        String key = "ratelimit:" + apiKeyId + ":" + windowIndex;

        // TTL buffer to ensure key persists until window rollover completes
        int keyTtl = windowSeconds * 2;

        try {
            List<?> result = redisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(keyTtl),
                    String.valueOf(maxRequests)
            );

            if (result == null || result.size() < 4) {
                throw new IllegalStateException("Unexpected response from Redis rate limit script");
            }

            long allowed = ((Number) result.get(0)).longValue();
            long remaining = ((Number) result.get(2)).longValue();
            long ttl = ((Number) result.get(3)).longValue();

            long resetSeconds = ttl > 0 ? ttl : Math.max(1, (windowIndex + 1) * windowSeconds - nowSeconds);

            if (allowed == 1) {
                return RateLimitResult.allowed(maxRequests, remaining, resetSeconds);
            } else {
                return RateLimitResult.denied(maxRequests, resetSeconds, "Request limit exceeded for current window");
            }

        } catch (Exception ex) {
            log.error("Redis rate limit failure for key {}: {}", apiKeyId, ex.getMessage());
            if (failOpen) {
                log.warn("Fail-open policy active: allowing request despite Redis outage");
                return RateLimitResult.fallbackAllowed(maxRequests, "Redis unavailable (fail-open)");
            } else {
                log.warn("Fail-closed policy active: denying request due to Redis outage");
                return RateLimitResult.fallbackDenied(maxRequests, "Rate limiting service temporarily unavailable");
            }
        }
    }

    public boolean isFailOpen() {
        return failOpen;
    }
}
