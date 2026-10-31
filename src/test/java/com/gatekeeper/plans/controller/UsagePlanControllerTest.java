package com.gatekeeper.plans.controller;

import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.common.exception.GlobalExceptionHandler;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.repository.UsagePlanRepository;
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

@WebMvcTest(UsagePlanController.class)
@Import(GlobalExceptionHandler.class)
class UsagePlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UsagePlanRepository usagePlanRepository;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("GET /api/v1/plans returns 200 OK with available usage plans")
    void shouldListUsagePlans() throws Exception {
        UsagePlan plan = new UsagePlan(
                UUID.randomUUID(),
                "Starter",
                60,
                60,
                Instant.now()
        );

        when(usagePlanRepository.findAll()).thenReturn(List.of(plan));

        mockMvc.perform(get("/api/v1/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Starter"))
                .andExpect(jsonPath("$[0].requestsPerWindow").value(60))
                .andExpect(jsonPath("$[0].windowSeconds").value(60));
    }
}
