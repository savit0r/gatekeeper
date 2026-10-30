package com.gatekeeper.identity.exception;

import com.gatekeeper.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class DeveloperNotFoundException extends ResourceNotFoundException {
    public DeveloperNotFoundException(UUID developerId) {
        super("Developer not found with id: " + developerId);
    }

    public DeveloperNotFoundException(String email) {
        super("Developer not found with email: " + email);
    }
}
