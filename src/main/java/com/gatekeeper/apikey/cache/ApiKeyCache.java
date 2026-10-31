package com.gatekeeper.apikey.cache;

import java.util.Optional;

public interface ApiKeyCache {

    /**
     * Looks up cached API key metadata by SHA-256 hash.
     */
    Optional<CachedApiKey> get(String keyHash);

    /**
     * Stores API key metadata in the cache with a defined TTL.
     */
    void put(String keyHash, CachedApiKey cachedApiKey);

    /**
     * Evicts cached API key metadata by SHA-256 hash.
     */
    void evict(String keyHash);
}
