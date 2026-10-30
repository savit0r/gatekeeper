package com.gatekeeper.apikey.dto;

import com.gatekeeper.apikey.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned ONLY when an API key is created or rotated.
 * This is the ONLY time the full rawKey is revealed to the caller.
 */
public record ApiKeyCreatedResponse(
        UUID id,
        String name,
        String rawKey,
        String keyPrefix,
        UUID planId,
        String planName,
        ApiKeyStatus status,
        Instant expiresAt,
        Instant createdAt
) {}
