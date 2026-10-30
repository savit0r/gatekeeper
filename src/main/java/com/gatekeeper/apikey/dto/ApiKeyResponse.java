package com.gatekeeper.apikey.dto;

import com.gatekeeper.apikey.domain.ApiKeyStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard API key representation for listing and retrieval.
 * Raw key is omitted because only the hash is retained in persistent storage.
 */
public record ApiKeyResponse(
        UUID id,
        String name,
        String keyPrefix,
        UUID planId,
        String planName,
        ApiKeyStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {}
