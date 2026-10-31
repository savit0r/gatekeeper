package com.gatekeeper.usage.controller;

import com.gatekeeper.usage.dto.UsageEventResponse;
import com.gatekeeper.usage.dto.UsageSummaryResponse;
import com.gatekeeper.usage.service.UsageEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/analytics")
public class UsageAnalyticsController {

    private final UsageEventService usageEventService;

    public UsageAnalyticsController(UsageEventService usageEventService) {
        this.usageEventService = usageEventService;
    }

    @GetMapping("/keys/{keyId}/summary")
    public ResponseEntity<UsageSummaryResponse> getSummary(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @PathVariable UUID keyId) {
        UsageSummaryResponse summary = usageEventService.getUsageSummary(developerId, keyId);
        return ResponseEntity.ok(summary);
    }

    @GetMapping("/keys/{keyId}/events")
    public ResponseEntity<List<UsageEventResponse>> getRecentEvents(
            @RequestHeader("X-Developer-Id") UUID developerId,
            @PathVariable UUID keyId) {
        List<UsageEventResponse> events = usageEventService.getRecentEvents(developerId, keyId);
        return ResponseEntity.ok(events);
    }
}
