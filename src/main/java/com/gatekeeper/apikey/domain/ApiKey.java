package com.gatekeeper.apikey.domain;

import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.plans.domain.UsagePlan;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "developer_id", nullable = false)
    private Developer developer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private UsagePlan plan;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
        // Required by JPA
    }

    public ApiKey(UUID id, Developer developer, UsagePlan plan, String name, String keyPrefix, String keyHash, Instant expiresAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.developer = developer;
        this.plan = plan;
        this.name = name;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.status = ApiKeyStatus.ACTIVE;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public boolean isActive() {
        if (this.status != ApiKeyStatus.ACTIVE) {
            return false;
        }
        if (this.expiresAt != null && Instant.now().isAfter(this.expiresAt)) {
            return false;
        }
        return true;
    }

    public void revoke() {
        this.status = ApiKeyStatus.REVOKED;
        this.revokedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Developer getDeveloper() {
        return developer;
    }

    public UsagePlan getPlan() {
        return plan;
    }

    public void setPlan(UsagePlan plan) {
        this.plan = plan;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public void setStatus(ApiKeyStatus status) {
        this.status = status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApiKey apiKey)) return false;
        return Objects.equals(id, apiKey.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
