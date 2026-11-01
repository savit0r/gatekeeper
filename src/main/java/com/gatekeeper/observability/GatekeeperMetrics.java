package com.gatekeeper.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class GatekeeperMetrics {

    private final MeterRegistry registry;

    public GatekeeperMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordRateLimitAllowed(String planName) {
        registry.counter("gatekeeper.ratelimit.requests", "status", "allowed", "plan", planName != null ? planName : "unknown").increment();
    }

    public void recordRateLimitRejected(String planName) {
        registry.counter("gatekeeper.ratelimit.requests", "status", "rejected", "plan", planName != null ? planName : "unknown").increment();
    }

    public void recordCacheHit() {
        registry.counter("gatekeeper.keycache.lookups", "result", "hit").increment();
    }

    public void recordCacheMiss() {
        registry.counter("gatekeeper.keycache.lookups", "result", "miss").increment();
    }

    public void recordKeyAction(String action) {
        registry.counter("gatekeeper.key.lifecycle", "action", action).increment();
    }

    public void recordLogin(String result) {
        registry.counter("gatekeeper.auth.logins", "result", result).increment();
    }

    public void recordGatewayLatency(long latencyMs) {
        registry.timer("gatekeeper.gateway.latency").record(Duration.ofMillis(latencyMs));
    }
}
