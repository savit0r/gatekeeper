package com.gatekeeper.gateway.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimiter rateLimiterFailClosed;
    private RedisRateLimiter rateLimiterFailOpen;

    @BeforeEach
    void setUp() {
        rateLimiterFailClosed = new RedisRateLimiter(redisTemplate, false);
        rateLimiterFailOpen = new RedisRateLimiter(redisTemplate, true);
    }

    @Test
    @DisplayName("Should allow request when counter is within limit")
    void shouldAllowWhenWithinLimit() {
        UUID keyId = UUID.randomUUID();
        // Result: [allowed=1, current=1, remaining=9, ttl=60]
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("120"), eq("10")))
                .thenReturn(List.of(1L, 1L, 9L, 60L));

        RateLimitResult result = rateLimiterFailClosed.checkRateLimit(keyId, 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.resetSeconds()).isEqualTo(60);
        assertThat(result.isFallback()).isFalse();
    }

    @Test
    @DisplayName("Should deny request with 429 when counter exceeds limit")
    void shouldDenyWhenLimitExceeded() {
        UUID keyId = UUID.randomUUID();
        // Result: [allowed=0, current=11, remaining=0, ttl=45]
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), eq("120"), eq("10")))
                .thenReturn(List.of(0L, 11L, 0L, 45L));

        RateLimitResult result = rateLimiterFailClosed.checkRateLimit(keyId, 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.resetSeconds()).isEqualTo(45);
        assertThat(result.reason()).contains("Request limit exceeded");
    }

    @Test
    @DisplayName("Should fail-closed when Redis is unavailable and failOpen is false")
    void shouldFailClosedOnRedisOutage() {
        UUID keyId = UUID.randomUUID();
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        RateLimitResult result = rateLimiterFailClosed.checkRateLimit(keyId, 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.isFallback()).isTrue();
        assertThat(result.reason()).contains("Rate limiting service temporarily unavailable");
    }

    @Test
    @DisplayName("Should fail-open when Redis is unavailable and failOpen is true")
    void shouldFailOpenOnRedisOutage() {
        UUID keyId = UUID.randomUUID();
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));

        RateLimitResult result = rateLimiterFailOpen.checkRateLimit(keyId, 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.isFallback()).isTrue();
        assertThat(result.reason()).contains("fail-open");
    }
}
