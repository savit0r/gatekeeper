package com.gatekeeper.usage.service;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.exception.ApiKeyNotFoundException;
import com.gatekeeper.apikey.repository.ApiKeyRepository;
import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.domain.UsageEvent;
import com.gatekeeper.usage.dto.UsageEventResponse;
import com.gatekeeper.usage.dto.UsageSummaryResponse;
import com.gatekeeper.usage.repository.UsageEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class UsageEventService {

    private static final Logger log = LoggerFactory.getLogger(UsageEventService.class);

    private final UsageEventRepository usageEventRepository;
    private final ApiKeyRepository apiKeyRepository;

    public UsageEventService(UsageEventRepository usageEventRepository, ApiKeyRepository apiKeyRepository) {
        this.usageEventRepository = usageEventRepository;
        this.apiKeyRepository = apiKeyRepository;
    }

    @Async
    public void recordEventAsync(UUID apiKeyId, String route, int statusCode, long latencyMs,
                                 UsageDecision decision, String rejectionReason) {
        try {
            recordEvent(apiKeyId, route, statusCode, latencyMs, decision, rejectionReason);
        } catch (Exception ex) {
            log.error("Failed to asynchronously record usage event for key {}: {}", apiKeyId, ex.getMessage());
        }
    }

    public UsageEvent recordEvent(UUID apiKeyId, String route, int statusCode, long latencyMs,
                                  UsageDecision decision, String rejectionReason) {
        ApiKey apiKey = null;
        if (apiKeyId != null) {
            apiKey = apiKeyRepository.findById(apiKeyId).orElse(null);
        }

        UsageEvent event = new UsageEvent(
                UUID.randomUUID(),
                apiKey,
                route,
                statusCode,
                latencyMs,
                decision,
                rejectionReason
        );

        return usageEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public UsageSummaryResponse getUsageSummary(UUID developerId, UUID apiKeyId) {
        ApiKey apiKey = verifyKeyOwnership(developerId, apiKeyId);

        long total = usageEventRepository.countByApiKeyId(apiKeyId);
        long allowed = usageEventRepository.countByApiKeyIdAndDecision(apiKeyId, UsageDecision.ALLOWED);
        long rejected = usageEventRepository.countByApiKeyIdAndDecision(apiKeyId, UsageDecision.REJECTED);
        Double avgLatency = usageEventRepository.getAverageLatencyByApiKeyId(apiKeyId);

        return new UsageSummaryResponse(
                apiKeyId,
                apiKey.getName(),
                total,
                allowed,
                rejected,
                avgLatency != null ? avgLatency : 0.0
        );
    }

    @Transactional(readOnly = true)
    public List<UsageEventResponse> getRecentEvents(UUID developerId, UUID apiKeyId) {
        verifyKeyOwnership(developerId, apiKeyId);

        return usageEventRepository.findTop50ByApiKeyIdOrderByOccurredAtDesc(apiKeyId).stream()
                .map(event -> new UsageEventResponse(
                        event.getId(),
                        event.getOccurredAt(),
                        event.getRoute(),
                        event.getStatusCode(),
                        event.getLatencyMs(),
                        event.getDecision(),
                        event.getRejectionReason()
                ))
                .toList();
    }

    private ApiKey verifyKeyOwnership(UUID developerId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findByIdWithDetails(apiKeyId)
                .orElseThrow(() -> new ApiKeyNotFoundException(apiKeyId));

        if (!apiKey.getDeveloper().getId().equals(developerId)) {
            throw new ApiKeyNotFoundException(apiKeyId);
        }
        return apiKey;
    }
}
