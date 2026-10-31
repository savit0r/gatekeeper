package com.gatekeeper.gateway.ratelimit;

import java.util.UUID;

public interface RateLimiter {
    RateLimitResult checkRateLimit(UUID apiKeyId, int maxRequests, int windowSeconds);
}
