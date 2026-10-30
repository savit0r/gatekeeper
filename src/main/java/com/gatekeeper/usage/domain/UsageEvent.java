package com.gatekeeper.usage.domain;

import com.gatekeeper.apikey.domain.ApiKey;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "usage_events")
public class UsageEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_key_id")
    private ApiKey apiKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "route", nullable = false, length = 255)
    private String route;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private UsageDecision decision;

    @Column(name = "rejection_reason", length = 50)
    private String rejectionReason;

    protected UsageEvent() {
        // Required by JPA
    }

    public UsageEvent(UUID id, ApiKey apiKey, String route, int statusCode, long latencyMs,
                      UsageDecision decision, String rejectionReason) {
        this.id = id != null ? id : UUID.randomUUID();
        this.apiKey = apiKey;
        this.route = route;
        this.statusCode = statusCode;
        this.latencyMs = latencyMs;
        this.decision = decision;
        this.rejectionReason = rejectionReason;
        this.occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getRoute() {
        return route;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public UsageDecision getDecision() {
        return decision;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsageEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
