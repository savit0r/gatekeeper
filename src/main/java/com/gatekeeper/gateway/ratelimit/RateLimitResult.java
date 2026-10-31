package com.gatekeeper.gateway.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetSeconds,
        boolean isFallback,
        String reason
) {
    public static RateLimitResult allowed(long limit, long remaining, long resetSeconds) {
        return new RateLimitResult(true, limit, remaining, resetSeconds, false, null);
    }

    public static RateLimitResult denied(long limit, long resetSeconds, String reason) {
        return new RateLimitResult(false, limit, 0, resetSeconds, false, reason);
    }

    public static RateLimitResult fallbackAllowed(long limit, String reason) {
        return new RateLimitResult(true, limit, limit, 0, true, reason);
    }

    public static RateLimitResult fallbackDenied(long limit, String reason) {
        return new RateLimitResult(false, limit, 0, 0, true, reason);
    }
}
