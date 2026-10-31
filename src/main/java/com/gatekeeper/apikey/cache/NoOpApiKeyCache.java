package com.gatekeeper.apikey.cache;

import java.util.Optional;

public class NoOpApiKeyCache implements ApiKeyCache {

    @Override
    public Optional<CachedApiKey> get(String keyHash) {
        return Optional.empty();
    }

    @Override
    public void put(String keyHash, CachedApiKey cachedApiKey) {
        // no-op
    }

    @Override
    public void evict(String keyHash) {
        // no-op
    }
}
