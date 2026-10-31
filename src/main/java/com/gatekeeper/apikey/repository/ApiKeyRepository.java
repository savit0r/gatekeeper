package com.gatekeeper.apikey.repository;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.domain.ApiKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.plan JOIN FETCH k.developer WHERE k.keyPrefix = :keyPrefix AND k.status = :status")
    List<ApiKey> findByKeyPrefixAndStatus(@Param("keyPrefix") String keyPrefix, @Param("status") ApiKeyStatus status);

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.plan JOIN FETCH k.developer WHERE k.developer.id = :developerId")
    List<ApiKey> findByDeveloperId(@Param("developerId") UUID developerId);

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.plan JOIN FETCH k.developer WHERE k.id = :id")
    Optional<ApiKey> findByIdWithDetails(@Param("id") UUID id);

    Optional<ApiKey> findByKeyHash(String keyHash);

    boolean existsByKeyHash(String keyHash);
}
