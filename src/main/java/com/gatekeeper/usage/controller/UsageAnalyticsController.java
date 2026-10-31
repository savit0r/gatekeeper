package com.gatekeeper.usage.controller;

import com.gatekeeper.usage.dto.UsageEventResponse;
import com.gatekeeper.usage.dto.UsageSummaryResponse;
import com.gatekeeper.usage.service.UsageEventService;
import com.gatekeeper.identity.security.DeveloperSecurityResolver;
import com.gatekeeper.identity.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class UsageAnalyticsController {

    private final UsageEventService usageEventService;
    private final DeveloperSecurityResolver securityResolver;

    @Autowired
    public UsageAnalyticsController(UsageEventService usageEventService, DeveloperSecurityResolver securityResolver) {
        this.usageEventService = usageEventService;
        this.securityResolver = securityResolver;
    }

    public UsageAnalyticsController(UsageEventService usageEventService) {
        this(usageEventService, new DeveloperSecurityResolver(new JwtService("gatekeeper-default-super-secret-jwt-signing-key-32bytes-long!", 86400)));
    }

    @GetMapping("/keys/{keyId}/summary")
    public ResponseEntity<UsageSummaryResponse> getSummary(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @PathVariable UUID keyId) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        UsageSummaryResponse summary = usageEventService.getUsageSummary(authenticatedDevId, keyId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/keys/{keyId}/events")
    public ResponseEntity<List<UsageEventResponse>> getRecentEvents(
            HttpServletRequest servletRequest,
            @RequestHeader(value = "X-Developer-Id", required = false) UUID developerId,
            @PathVariable UUID keyId) {
        UUID authenticatedDevId = securityResolver.resolveDeveloperId(servletRequest, developerId);
        List<UsageEventResponse> events = usageEventService.getRecentEvents(authenticatedDevId, keyId);
        return ResponseEntity.ok(events);
    }
}
