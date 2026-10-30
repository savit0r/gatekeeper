package com.gatekeeper.apikey.controller;

import com.gatekeeper.apikey.domain.ApiKeyStatus;
import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.ApiKeyResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
@Import(GlobalExceptionHandler.class)
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @Test
    @DisplayName("POST /api/v1/keys creates key and returns 201 with raw key shown once")
    void shouldCreateApiKey() throws Exception {
        UUID devId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        String requestJson = """
                {
                    "name": "Production Key",
                    "planId": "%s"
                }
                """.formatted(planId);

        ApiKeyCreatedResponse response = new ApiKeyCreatedResponse(
                keyId,
                "Production Key",
                "gk_live_12345678_abcdef123456",
                "gk_live_12345678",
                planId,
                "Pro Plan",
                ApiKeyStatus.ACTIVE,
                null,
                Instant.now()
        );

        when(apiKeyService.createApiKey(eq(devId), any(CreateApiKeyRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/keys")
                        .header("X-Developer-Id", devId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(keyId.toString()))
                .andExpect(jsonPath("$.name").value("Production Key"))
                .andExpect(jsonPath("$.rawKey").value("gk_live_12345678_abcdef123456"))
                .andExpect(jsonPath("$.keyPrefix").value("gk_live_12345678"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/keys lists keys without revealing raw keys")
    void shouldListApiKeys() throws Exception {
        UUID devId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        ApiKeyResponse keyResponse = new ApiKeyResponse(
                keyId,
                "Default Key",
                "gk_live_87654321",
                UUID.randomUUID(),
                "Starter",
                ApiKeyStatus.ACTIVE,
                null,
                Instant.now(),
                null
        );

        when(apiKeyService.listApiKeys(devId)).thenReturn(List.of(keyResponse));

        mockMvc.perform(get("/api/v1/keys")
                        .header("X-Developer-Id", devId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(keyId.toString()))
                .andExpect(jsonPath("$[0].name").value("Default Key"))
                .andExpect(jsonPath("$[0].keyPrefix").value("gk_live_87654321"))
                .andExpect(jsonPath("$[0].rawKey").doesNotExist());
    }

    @Test
    @DisplayName("DELETE /api/v1/keys/{id} revokes key and returns 204 No Content")
    void shouldRevokeApiKey() throws Exception {
        UUID devId = UUID.randomUUID();
        UUID keyId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/keys/" + keyId)
                        .header("X-Developer-Id", devId.toString()))
                .andExpect(status().isNoContent());

        verify(apiKeyService).revokeApiKey(devId, keyId);
    }

    @Test
    @DisplayName("POST /api/v1/keys with invalid input returns 400 Bad Request")
    void shouldRejectInvalidRequestBody() throws Exception {
        UUID devId = UUID.randomUUID();
        String invalidJson = """
                {
                    "name": "",
                    "planId": null
                }
                """;

        mockMvc.perform(post("/api/v1/keys")
                        .header("X-Developer-Id", devId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.planId").exists());
    }
}
