package com.gatekeeper.apikey.repository;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {
    List<ApiKey> findByKeyPrefixAndStatus(String keyPrefix, ApiKeyStatus status);
    List<ApiKey> findByDeveloperId(UUID developerId);
    Optional<ApiKey> findByKeyHash(String keyHash);
    boolean existsByKeyHash(String keyHash);
}
