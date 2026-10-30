package com.gatekeeper.usage.repository;

import com.gatekeeper.usage.domain.UsageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UsageEventRepository extends JpaRepository<UsageEvent, UUID> {
    List<UsageEvent> findByApiKeyIdOrderByOccurredAtDesc(UUID apiKeyId);
}
