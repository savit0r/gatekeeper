package com.gatekeeper.apikey.controller;

import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.ApiKeyResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.identity.security.DeveloperSecurityResolver;
import com.gatekeeper.identity.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final DeveloperSecurityResolver securityResolver;

    @Autowired
    public ApiKeyController(ApiKeyService apiKeyService, DeveloperSecurityResolver securityResolver) {
        this.apiKeyService = apiKeyService;
        this.securityResolver = securityResolver;
    }

    public ApiKeyController(ApiKeyService apiKeyService) {
        this(apiKeyService, new DeveloperSecurityResolver(new JwtService("gatekeeper-default-super-secret-jwt-signing-key-32bytes-long!", 86400)));
    }

    @PostMapping
    public ResponseEntity<ApiKeyCreatedResponse> createKey(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @Valid @RequestBody CreateApiKeyRequest request) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        ApiKeyCreatedResponse created = apiKeyService.createApiKey(authenticatedDevId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> listKeys(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        List<ApiKeyResponse> keys = apiKeyService.listApiKeys(authenticatedDevId);
        return ResponseEntity.ok(keys);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiKeyResponse> getKey(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @PathVariable UUID id) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        ApiKeyResponse key = apiKeyService.getApiKey(authenticatedDevId, id);
        return ResponseEntity.ok(key);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeKey(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @PathVariable UUID id) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        apiKeyService.revokeApiKey(authenticatedDevId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/rotate")
    public ResponseEntity<ApiKeyCreatedResponse> rotateKey(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @PathVariable UUID id) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        ApiKeyCreatedResponse rotated = apiKeyService.rotateApiKey(authenticatedDevId, id);
        return ResponseEntity.ok(rotated);
    }
}
