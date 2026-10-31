package com.gatekeeper.usage.service;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.exception.ApiKeyNotFoundException;
import com.gatekeeper.apikey.repository.ApiKeyRepository;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.domain.UsageEvent;
import com.gatekeeper.usage.dto.UsageEventResponse;
import com.gatekeeper.usage.dto.UsageSummaryResponse;
import com.gatekeeper.usage.repository.UsageEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UsageEventServiceTest {

    @Mock
    private UsageEventRepository usageEventRepository;

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private UsageEventService usageEventService;
    private Developer developer;
    private UsagePlan plan;
    private ApiKey apiKey;

    @BeforeEach
    void setUp() {
        usageEventService = new UsageEventService(usageEventRepository, apiKeyRepository);

        developer = new Developer(UUID.randomUUID(), "dev@gatekeeper.dev", "hash", "ROLE_DEVELOPER", Instant.now());
        plan = new UsagePlan(UUID.randomUUID(), "Starter", 100, 60, Instant.now());
        apiKey = new ApiKey(UUID.randomUUID(), developer, plan, "Key", "gk_live_12345678", "hash", null);
    }

    @Test
    @DisplayName("Should record usage event correctly")
    void shouldRecordUsageEvent() {
        when(apiKeyRepository.findById(apiKey.getId())).thenReturn(Optional.of(apiKey));
        when(usageEventRepository.save(any(UsageEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UsageEvent saved = usageEventService.recordEvent(
                apiKey.getId(),
                "/api/v1/protected/hello",
                200,
                12L,
                UsageDecision.ALLOWED,
                null
        );

        assertThat(saved.getRoute()).isEqualTo("/api/v1/protected/hello");
        assertThat(saved.getStatusCode()).isEqualTo(200);
        assertThat(saved.getLatencyMs()).isEqualTo(12L);
        assertThat(saved.getDecision()).isEqualTo(UsageDecision.ALLOWED);
        assertThat(saved.getApiKey()).isEqualTo(apiKey);

        ArgumentCaptor<UsageEvent> captor = ArgumentCaptor.forClass(UsageEvent.class);
        verify(usageEventRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(UsageDecision.ALLOWED);
    }

    @Test
    @DisplayName("Should aggregate usage summary correctly for authorized developer")
    void shouldGetUsageSummary() {
        when(apiKeyRepository.findByIdWithDetails(apiKey.getId())).thenReturn(Optional.of(apiKey));
        when(usageEventRepository.countByApiKeyId(apiKey.getId())).thenReturn(10L);
        when(usageEventRepository.countByApiKeyIdAndDecision(apiKey.getId(), UsageDecision.ALLOWED)).thenReturn(8L);
        when(usageEventRepository.countByApiKeyIdAndDecision(apiKey.getId(), UsageDecision.REJECTED)).thenReturn(2L);
        when(usageEventRepository.getAverageLatencyByApiKeyId(apiKey.getId())).thenReturn(15.5);

        UsageSummaryResponse summary = usageEventService.getUsageSummary(developer.getId(), apiKey.getId());

        assertThat(summary.apiKeyId()).isEqualTo(apiKey.getId());
        assertThat(summary.keyName()).isEqualTo(apiKey.getName());
        assertThat(summary.totalRequests()).isEqualTo(10L);
        assertThat(summary.allowedRequests()).isEqualTo(8L);
        assertThat(summary.rejectedRequests()).isEqualTo(2L);
        assertThat(summary.averageLatencyMs()).isEqualTo(15.5);
    }

    @Test
    @DisplayName("Should reject summary request when developer does not own the API key")
    void shouldRejectUnauthorizedDeveloperForSummary() {
        when(apiKeyRepository.findByIdWithDetails(apiKey.getId())).thenReturn(Optional.of(apiKey));

        UUID differentDevId = UUID.randomUUID();
        assertThatThrownBy(() -> usageEventService.getUsageSummary(differentDevId, apiKey.getId()))
                .isInstanceOf(ApiKeyNotFoundException.class);
    }

    @Test
    @DisplayName("Should retrieve recent events for authorized developer")
    void shouldGetRecentEvents() {
        when(apiKeyRepository.findByIdWithDetails(apiKey.getId())).thenReturn(Optional.of(apiKey));

        UsageEvent event = new UsageEvent(
                UUID.randomUUID(),
                apiKey,
                "/api/v1/protected/hello",
                200,
                10L,
                UsageDecision.ALLOWED,
                null
        );
        when(usageEventRepository.findTop50ByApiKeyIdOrderByOccurredAtDesc(apiKey.getId()))
                .thenReturn(List.of(event));

        List<UsageEventResponse> events = usageEventService.getRecentEvents(developer.getId(), apiKey.getId());

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().route()).isEqualTo("/api/v1/protected/hello");
        assertThat(events.getFirst().statusCode()).isEqualTo(200);
    }
}
