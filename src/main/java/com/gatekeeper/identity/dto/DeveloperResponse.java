package com.gatekeeper.identity.dto;

import java.time.Instant;
import java.util.UUID;

public record DeveloperResponse(
        UUID id,
        String email,
        String role,
        Instant createdAt
) {}
