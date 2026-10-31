package com.gatekeeper.usage.controller;

import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.common.exception.GlobalExceptionHandler;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.dto.UsageEventResponse;
import com.gatekeeper.usage.dto.UsageSummaryResponse;
import com.gatekeeper.usage.service.UsageEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UsageAnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class UsageAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsageEventService usageEventService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("GET /api/v1/analytics/keys/{keyId}/summary returns 200 OK with summary metrics")
    void shouldReturnUsageSummary() throws Exception {
        UUID devId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        UsageSummaryResponse summary = new UsageSummaryResponse(
                keyId,
                "Prod Key",
                100L,
                95L,
                5L,
                12.5
        );

        when(usageEventService.getUsageSummary(devId, keyId)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/analytics/keys/" + keyId + "/summary")
                        .header("X-Developer-Id", devId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyId").value(keyId.toString()))
                .andExpect(jsonPath("$.keyName").value("Prod Key"))
                .andExpect(jsonPath("$.totalRequests").value(100))
                .andExpect(jsonPath("$.allowedRequests").value(95))
                .andExpect(jsonPath("$.rejectedRequests").value(5))
                .andExpect(jsonPath("$.averageLatencyMs").value(12.5));
    }

    @Test
    @DisplayName("GET /api/v1/analytics/keys/{keyId}/events returns 200 OK with recent events")
    void shouldReturnRecentEvents() throws Exception {
        UUID devId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        UsageEventResponse event = new UsageEventResponse(
                eventId,
                Instant.now(),
                "/api/v1/protected/hello",
                200,
                8L,
                UsageDecision.ALLOWED,
                null
        );

        when(usageEventService.getRecentEvents(devId, keyId)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/v1/analytics/keys/" + keyId + "/events")
                        .header("X-Developer-Id", devId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId.toString()))
                .andExpect(jsonPath("$[0].route").value("/api/v1/protected/hello"))
                .andExpect(jsonPath("$[0].statusCode").value(200))
                .andExpect(jsonPath("$[0].decision").value("ALLOWED"));
    }
}
