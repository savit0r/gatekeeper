package com.gatekeeper.identity.security;

import java.time.Instant;
import java.util.UUID;

public record JwtClaims(
        UUID developerId,
        String email,
        String role,
        Instant expiresAt
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
