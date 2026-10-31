package com.gatekeeper.usage.repository;

import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.domain.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {

    List<UsageEvent> findByApiKeyIdOrderByOccurredAtDesc(UUID apiKeyId);

    List<UsageEvent> findTop50ByApiKeyIdOrderByOccurredAtDesc(UUID apiKeyId);

    long countByApiKeyId(UUID apiKeyId);

    long countByApiKeyIdAndDecision(UUID apiKeyId, UsageDecision decision);

    @Query("SELECT COALESCE(AVG(u.latencyMs), 0.0) FROM UsageEvent u WHERE u.apiKey.id = :apiKeyId")
    Double getAverageLatencyByApiKeyId(@Param("apiKeyId") UUID apiKeyId);
}
