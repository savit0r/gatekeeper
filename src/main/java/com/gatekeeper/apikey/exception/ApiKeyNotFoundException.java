package com.gatekeeper.apikey.exception;

import com.gatekeeper.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class ApiKeyNotFoundException extends ResourceNotFoundException {
    public ApiKeyNotFoundException(UUID keyId) {
        super("API key not found with id: " + keyId);
    }
}
