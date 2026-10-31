package com.gatekeeper.plans.dto;

import java.time.Instant;
import java.util.UUID;

public record UsagePlanResponse(
        UUID id,
        String name,
        int requestsPerWindow,
        int windowSeconds,
        Instant createdAt
) {}
