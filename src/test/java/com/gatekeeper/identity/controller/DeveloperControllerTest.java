package com.gatekeeper.identity.controller;

import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.common.exception.GlobalExceptionHandler;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.identity.dto.DeveloperResponse;
import com.gatekeeper.identity.dto.RegisterDeveloperRequest;
import com.gatekeeper.identity.service.DeveloperService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.gatekeeper.identity.security.DeveloperSecurityResolver;
import com.gatekeeper.identity.security.JwtService;

@WebMvcTest(DeveloperController.class)
@Import({GlobalExceptionHandler.class, DeveloperSecurityResolver.class, JwtService.class})
class DeveloperControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeveloperService developerService;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("POST /api/v1/developers/register creates developer and returns 201 Created")
    void shouldRegisterDeveloper() throws Exception {
        UUID devId = UUID.randomUUID();
        DeveloperResponse response = new DeveloperResponse(
                devId,
                "dan@gatekeeper.dev",
                "ROLE_DEVELOPER",
                Instant.now()
        );

        when(developerService.registerDeveloper(any(RegisterDeveloperRequest.class))).thenReturn(response);

        String requestJson = """
                {
                    "email": "dan@gatekeeper.dev",
                    "password": "strongPassword123"
                }
                """;

        mockMvc.perform(post("/api/v1/developers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(devId.toString()))
                .andExpect(jsonPath("$.email").value("dan@gatekeeper.dev"))
                .andExpect(jsonPath("$.role").value("ROLE_DEVELOPER"));
    }

    @Test
    @DisplayName("POST /api/v1/developers/register rejects invalid email or short password with 400 Bad Request")
    void shouldRejectInvalidInput() throws Exception {
        String invalidJson = """
                {
                    "email": "not-an-email",
                    "password": "short"
                }
                """;

        mockMvc.perform(post("/api/v1/developers/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }

    @Test
    @DisplayName("GET /api/v1/developers/me returns 200 OK with developer profile")
    void shouldGetDeveloperProfile() throws Exception {
        UUID devId = UUID.randomUUID();
        DeveloperResponse response = new DeveloperResponse(
                devId,
                "dan@gatekeeper.dev",
                "ROLE_DEVELOPER",
                Instant.now()
        );

        when(developerService.getDeveloper(devId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/developers/me")
                        .header("X-Developer-Id", devId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(devId.toString()))
                .andExpect(jsonPath("$.email").value("dan@gatekeeper.dev"));
    }
}
