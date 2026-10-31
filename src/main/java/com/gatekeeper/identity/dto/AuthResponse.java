package com.gatekeeper.identity.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,
        UUID developerId,
        String email,
        String role
) {}
