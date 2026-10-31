package com.gatekeeper.identity.security;

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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeveloperRepository developerRepository;

    @Autowired
    private UsagePlanRepository usagePlanRepository;

    private String testEmail;
    private String testPassword;
    private UsagePlan testPlan;

    @BeforeEach
    void setUp() {
        testEmail = "auth-it-" + UUID.randomUUID() + "@gatekeeper.dev";
        testPassword = "SuperSecurePassword123!";

        testPlan = usagePlanRepository.save(new UsagePlan(
                UUID.randomUUID(),
                "Auth Test Plan " + UUID.randomUUID(),
                50,
                60,
                Instant.now()
        ));
    }

    @Test
    @DisplayName("Complete authentication lifecycle: register, login, extract JWT, access dashboard endpoints with Bearer token")
    void completeAuthLifecycle() throws Exception {
        // 1. Register
        mockMvc.perform(post("/api/v1/developers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(testEmail, testPassword)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(testEmail));

        // 2. Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "%s"
                                }
                                """.formatted(testEmail, testPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(responseBody);
        assertThat(matcher.find()).isTrue();
        String jwtToken = matcher.group(1);

        // 3. Access /api/v1/developers/me with Bearer token
        mockMvc.perform(get("/api/v1/developers/me")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(testEmail));

        // 4. Create API key with Bearer token
        mockMvc.perform(post("/api/v1/keys")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "name": "Auth Integration Key",
                                    "planId": "%s"
                                }
                                """.formatted(testPlan.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.rawKey").isString())
                .andExpect(jsonPath("$.keyPrefix").isString());

        // 5. Access without token or header -> 401
        mockMvc.perform(get("/api/v1/developers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 6. Access with tampered Bearer token -> 401
        mockMvc.perform(get("/api/v1/developers/me")
                        .header("Authorization", "Bearer " + jwtToken + "badSignature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        // 7. Login with wrong password -> 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "%s",
                                    "password": "WrongPassword!"
                                }
                                """.formatted(testEmail)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
