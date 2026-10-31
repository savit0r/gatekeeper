package com.gatekeeper.usage.dto;

import com.gatekeeper.usage.domain.UsageDecision;

import java.time.Instant;
import java.util.UUID;

public record UsageEventResponse(
        UUID id,
        Instant occurredAt,
        String route,
        int statusCode,
        long latencyMs,
        UsageDecision decision,
        String rejectionReason
) {}
