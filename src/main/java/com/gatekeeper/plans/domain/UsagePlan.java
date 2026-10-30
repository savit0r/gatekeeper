package com.gatekeeper.plans.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "usage_plans")
public class UsagePlan {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "requests_per_window", nullable = false)
    private int requestsPerWindow;

    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UsagePlan() {
        // Required by JPA
    }

    public UsagePlan(UUID id, String name, int requestsPerWindow, int windowSeconds, Instant createdAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.name = name;
        this.requestsPerWindow = requestsPerWindow;
        this.windowSeconds = windowSeconds;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRequestsPerWindow() {
        return requestsPerWindow;
    }

    public void setRequestsPerWindow(int requestsPerWindow) {
        this.requestsPerWindow = requestsPerWindow;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsagePlan usagePlan)) return false;
        return Objects.equals(id, usagePlan.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
