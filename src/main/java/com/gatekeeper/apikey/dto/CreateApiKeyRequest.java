package com.gatekeeper.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateApiKeyRequest(
        @NotBlank(message = "API key name must not be blank")
        @Size(max = 100, message = "API key name must not exceed 100 characters")
        String name,

        @NotNull(message = "Plan ID must be specified")
        UUID planId,

        Instant expiresAt
) {}
