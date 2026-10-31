package com.gatekeeper.identity.controller;

import com.gatekeeper.identity.dto.DeveloperResponse;
import com.gatekeeper.identity.dto.RegisterDeveloperRequest;
import com.gatekeeper.identity.service.DeveloperService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/developers")
public class DeveloperController {

    private final DeveloperService developerService;

    public DeveloperController(DeveloperService developerService) {
        this.developerService = developerService;
    }

    @PostMapping("/register")
    public ResponseEntity<DeveloperResponse> register(@Valid @RequestBody RegisterDeveloperRequest request) {
        DeveloperResponse response = developerService.registerDeveloper(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<DeveloperResponse> getProfile(@RequestHeader("X-Developer-Id") UUID developerId) {
        DeveloperResponse response = developerService.getDeveloper(developerId);
        return ResponseEntity.ok(response);
    }
}
