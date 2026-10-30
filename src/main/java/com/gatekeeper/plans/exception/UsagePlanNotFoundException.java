package com.gatekeeper.plans.exception;

import com.gatekeeper.common.exception.ResourceNotFoundException;

import java.util.UUID;

public class UsagePlanNotFoundException extends ResourceNotFoundException {
    public UsagePlanNotFoundException(UUID planId) {
        super("Usage plan not found with id: " + planId);
    }

    public UsagePlanNotFoundException(String name) {
        super("Usage plan not found with name: " + name);
    }
}
