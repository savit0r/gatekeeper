package com.gatekeeper.gateway;

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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeveloperRepository developerRepository;

    @Autowired
    private UsagePlanRepository usagePlanRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Developer developer;
    private UUID apiKeyId;
    private String rawApiKey;

    @BeforeEach
    void setUp() {
        // Clear Redis keys to ensure clean rate limit window for test
        var keys = redisTemplate.keys("ratelimit:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }

        developer = new Developer(
                UUID.randomUUID(),
                "rate-test-" + UUID.randomUUID() + "@gatekeeper.dev",
                "pwd_hash",
                "ROLE_DEVELOPER",
                Instant.now()
        );
        developerRepository.save(developer);

        // Plan with 2 requests per 60-second window
        UsagePlan plan = new UsagePlan(
                UUID.randomUUID(),
                "Rate-Limit-Plan-" + UUID.randomUUID(),
                2,
                60,
                Instant.now()
        );
        usagePlanRepository.save(plan);

        ApiKeyCreatedResponse created = apiKeyService.createApiKey(
                developer.getId(),
                new CreateApiKeyRequest("Test Key", plan.getId(), null)
        );
        apiKeyId = created.id();
        rawApiKey = created.rawKey();
    }

    @Test
    @DisplayName("Should enforce rate limits and record usage analytics: 2 allowed, 1 rate-limited, metrics reflect counts")
    void shouldEnforceRateLimitingUnderRealRedis() throws Exception {
        // 1st request -> Allowed (Remaining: 1)
        mockMvc.perform(get("/api/v1/protected/hello")
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "1"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(jsonPath("$.message").value("Hello! You have successfully accessed a Gatekeeper protected endpoint."))
                .andExpect(jsonPath("$.keyName").value("Test Key"));

        // 2nd request -> Allowed (Remaining: 0)
        mockMvc.perform(get("/api/v1/protected/hello")
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"));

        // 3rd request -> Blocked with HTTP 429 Too Many Requests
        mockMvc.perform(get("/api/v1/protected/hello")
                        .header("X-API-Key", rawApiKey))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("X-RateLimit-Limit", "2"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Rate limit exceeded")));

        // Give async event processing a moment to persist
        Thread.sleep(150);

        // Verify analytics summary endpoint reflects the 3 requests (2 allowed, 1 rejected)
        mockMvc.perform(get("/api/v1/analytics/keys/" + apiKeyId + "/summary")
                        .header("X-Developer-Id", developer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyId").value(apiKeyId.toString()))
                .andExpect(jsonPath("$.totalRequests").value(3))
                .andExpect(jsonPath("$.allowedRequests").value(2))
                .andExpect(jsonPath("$.rejectedRequests").value(1));

        // Verify recent events endpoint returns the logged records
        mockMvc.perform(get("/api/v1/analytics/keys/" + apiKeyId + "/events")
                        .header("X-Developer-Id", developer.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when X-API-Key header is missing")
    void shouldRejectMissingApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/protected/hello"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Missing X-API-Key header"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when X-API-Key is invalid")
    void shouldRejectInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/protected/hello")
                        .header("X-API-Key", "gk_live_00000000_invalidsecret"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid, expired, or revoked API key"));
    }
}
