package com.gatekeeper.apikey.cache;

import com.gatekeeper.apikey.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

public record CachedApiKey(
        UUID id,
        String name,
        String keyPrefix,
        String keyHash,
        UUID developerId,
        UUID planId,
        String planName,
        int requestsPerWindow,
        int windowSeconds,
        ApiKeyStatus status,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
