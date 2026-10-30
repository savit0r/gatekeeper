package com.gatekeeper.apikey.controller;

import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.ApiKeyResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.service.ApiKeyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    public ApiKeyController(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createKey(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(developerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(
            @RequestHeader("X-Developer-Id") UUID developerId) {
        List<ApiKeyResponse> keys = apiKeyService.listApiKeys(developerId);
        return ResponseEntity.ok(keys);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> getKey(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @PathVariable UUID id) {
        ApiKeyResponse key = apiKeyService.getApiKey(developerId, id);
        return ResponseEntity.ok(key);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @PathVariable UUID id) {
        apiKeyService.revokeApiKey(developerId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiKeyCreatedResponse> rotateKey(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @PathVariable UUID id) {
        ApiKeyCreatedResponse rotated = apiKeyService.rotateApiKey(developerId, id);
        return ResponseEntity.ok(rotated);
    }
}
