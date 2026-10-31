package com.gatekeeper.usage.dto;

import java.util.UUID;

public record UsageSummaryResponse(
        UUID apiKeyId,
        String keyName,
        long totalRequests,
        long allowedRequests,
        long rejectedRequests,
        double averageLatencyMs
) {}
