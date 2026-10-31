package com.gatekeeper.identity.controller;

import com.gatekeeper.common.exception.GlobalExceptionHandler;
import com.gatekeeper.common.exception.UnauthorizedException;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.identity.dto.AuthResponse;
import com.gatekeeper.identity.dto.LoginRequest;
import com.gatekeeper.identity.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private com.gatekeeper.apikey.service.ApiKeyService apiKeyService;

    @MockitoBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("POST /api/v1/auth/login returns 200 OK with Bearer token on valid credentials")
    void shouldLoginSuccessfully() throws Exception {
        UUID devId = UUID.randomUUID();
        AuthResponse response = new AuthResponse(
                "mock.jwt.token",
                "Bearer",
                86400,
                devId,
                "dev@gatekeeper.dev",
                "ROLE_DEVELOPER"
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "dev@gatekeeper.dev",
                                    "password": "Password123!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("mock.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(86400))
                .andExpect(jsonPath("$.developerId").value(devId.toString()))
                .andExpect(jsonPath("$.email").value("dev@gatekeeper.dev"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 401 Unauthorized on invalid credentials")
    void shouldRejectInvalidCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "dev@gatekeeper.dev",
                                    "password": "WrongPassword!"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login returns 400 Bad Request on invalid request body")
    void shouldValidateRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "not-an-email",
                                    "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.email").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());
    }
}
