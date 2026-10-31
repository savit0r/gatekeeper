package com.gatekeeper.identity.controller;

import com.gatekeeper.identity.dto.DeveloperResponse;
import com.gatekeeper.identity.dto.RegisterDeveloperRequest;
import com.gatekeeper.identity.service.DeveloperService;
import com.gatekeeper.identity.security.DeveloperSecurityResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/developers")
public class DeveloperController {

    private final DeveloperService developerService;
    private final DeveloperSecurityResolver securityResolver;

    public DeveloperController(DeveloperService developerService, DeveloperSecurityResolver securityResolver) {
        this.developerService = developerService;
        this.securityResolver = securityResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<DeveloperResponse> register(@Valid @RequestBody RegisterDeveloperRequest request) {
        DeveloperResponse response = developerService.registerDeveloper(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<DeveloperResponse> getProfile(
            HttpServletRequest request,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(request, developerId);
        DeveloperResponse response = developerService.getDeveloper(authenticatedDevId);
        return ResponseEntity.ok(response);
    }
}
