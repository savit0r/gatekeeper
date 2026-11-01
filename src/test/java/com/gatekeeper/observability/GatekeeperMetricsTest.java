package com.gatekeeper.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatekeeperMetricsTest {

    private SimpleMeterRegistry registry;
    private GatekeeperMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new GatekeeperMetrics(registry);
    }

    @Test
    @DisplayName("recordRateLimitAllowed and recordRateLimitRejected increment appropriate counters with plan tag")
    void shouldRecordRateLimitMetrics() {
        metrics.recordRateLimitAllowed("Pro");
        metrics.recordRateLimitAllowed("Pro");
        metrics.recordRateLimitRejected("Starter");

        double allowedCount = registry.get("gatekeeper.ratelimit.requests")
                .tag("status", "allowed")
                .tag("plan", "Pro")
                .counter()
                .count();

        double rejectedCount = registry.get("gatekeeper.ratelimit.requests")
                .tag("status", "rejected")
                .tag("plan", "Starter")
                .counter()
                .count();

        assertThat(allowedCount).isEqualTo(2.0);
        assertThat(rejectedCount).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordCacheHit and recordCacheMiss increment cache lookup counters")
    void shouldRecordCacheMetrics() {
        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();

        double hits = registry.get("gatekeeper.keycache.lookups")
                .tag("result", "hit")
                .counter()
                .count();

        double misses = registry.get("gatekeeper.keycache.lookups")
                .tag("result", "miss")
                .counter()
                .count();

        assertThat(hits).isEqualTo(2.0);
        assertThat(misses).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordKeyAction increments key lifecycle counters")
    void shouldRecordKeyActions() {
        metrics.recordKeyAction("created");
        metrics.recordKeyAction("revoked");
        metrics.recordKeyAction("rotated");

        assertThat(registry.get("gatekeeper.key.lifecycle").tag("action", "created").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("gatekeeper.key.lifecycle").tag("action", "revoked").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("gatekeeper.key.lifecycle").tag("action", "rotated").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordLogin and recordGatewayLatency record values")
    void shouldRecordLoginAndLatency() {
        metrics.recordLogin("success");
        metrics.recordLogin("failure");
        metrics.recordGatewayLatency(15);

        assertThat(registry.get("gatekeeper.auth.logins").tag("result", "success").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("gatekeeper.auth.logins").tag("result", "failure").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("gatekeeper.gateway.latency").timer().count()).isEqualTo(1L);
    }
}
