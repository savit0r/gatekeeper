package com.gatekeeper.observability;

import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PrometheusEndpointIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeveloperRepository developerRepository;

    @Autowired
    private UsagePlanRepository usagePlanRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    private String rawApiKey;

    @BeforeEach
    void setUp() {
        Developer developer = developerRepository.save(new Developer(
                UUID.randomUUID(),
                "metrics-dev-" + UUID.randomUUID() + "@gatekeeper.dev",
                "pwd_hash",
                "ROLE_DEVELOPER",
                Instant.now()
        ));

        UsagePlan plan = usagePlanRepository.save(new UsagePlan(
                UUID.randomUUID(),
                "Metrics-Plan-" + UUID.randomUUID(),
                100,
                60,
                Instant.now()
        ));

        ApiKeyCreatedResponse created = apiKeyService.createApiKey(
                developer.getId(),
                new CreateApiKeyRequest("Metrics Key", plan.getId(), null)
        );
        rawApiKey = created.rawKey();
    }

    @Test
    @DisplayName("GET /actuator/health returns 200 UP")
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /actuator/prometheus exports custom Gatekeeper metric series")
    void prometheusEndpointExportsCustomMetrics() throws Exception {
        // Trigger gateway request to increment custom counters & latency timer
        mockMvc.perform(get("/api/v1/protected/hello")
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isOk());

        // Fetch Prometheus scrape output
        String prometheusOutput = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verify standard Prometheus scrape payload structure and custom metric names
        assertThat(prometheusOutput).contains("gatekeeper_ratelimit_requests_total");
        assertThat(prometheusOutput).contains("gatekeeper_keycache_lookups_total");
        assertThat(prometheusOutput).contains("gatekeeper_gateway_latency_seconds");
        assertThat(prometheusOutput).contains("gatekeeper_key_lifecycle_total");
    }
}
