package com.gatekeeper.apikey.crypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiKeyCryptoTest {

    private ApiKeyCrypto apiKeyCrypto;

    @BeforeEach
    void setUp() {
        apiKeyCrypto = new ApiKeyCrypto();
    }

    @Test
    @DisplayName("Should generate keys with correct format, prefix length, and 64-char SHA-256 hash")
    void shouldGenerateValidKeyStructure() {
        ApiKeyCrypto.GeneratedKey key = apiKeyCrypto.generateKey();

        assertThat(key.rawKey()).isNotBlank();
        assertThat(key.rawKey()).startsWith(ApiKeyCrypto.PREFIX_CONSTANT);
        assertThat(key.keyPrefix()).hasSize(16);
        assertThat(key.rawKey()).startsWith(key.keyPrefix() + "_");
        assertThat(key.keyHash()).hasSize(64);
        assertThat(key.keyHash()).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Should generate high-entropy unique keys with no collisions")
    void shouldGenerateUniqueKeys() {
        Set<String> rawKeys = new HashSet<>();
        Set<String> hashes = new HashSet<>();
        Set<String> prefixes = new HashSet<>();

        int count = 100;
        for (int i = 0; i < count; i++) {
            ApiKeyCrypto.GeneratedKey key = apiKeyCrypto.generateKey();
            rawKeys.add(key.rawKey());
            hashes.add(key.keyHash());
            prefixes.add(key.keyPrefix());
        }

        assertThat(rawKeys).hasSize(count);
        assertThat(hashes).hasSize(count);
        assertThat(prefixes).hasSize(count);
    }

    @Test
    @DisplayName("Should verify matching key correctly and reject tampering")
    void shouldVerifyKeyCorrectly() {
        ApiKeyCrypto.GeneratedKey key = apiKeyCrypto.generateKey();

        assertThat(apiKeyCrypto.verifyKey(key.rawKey(), key.keyHash())).isTrue();
        assertThat(apiKeyCrypto.verifyKey(key.rawKey() + "tamper", key.keyHash())).isFalse();
        assertThat(apiKeyCrypto.verifyKey("gk_live_00000000_differentsecret", key.keyHash())).isFalse();
        assertThat(apiKeyCrypto.verifyKey(null, key.keyHash())).isFalse();
        assertThat(apiKeyCrypto.verifyKey(key.rawKey(), null)).isFalse();
    }

    @Test
    @DisplayName("Should extract 16-character prefix or return null for malformed keys")
    void shouldExtractPrefixSafely() {
        ApiKeyCrypto.GeneratedKey key = apiKeyCrypto.generateKey();

        assertThat(apiKeyCrypto.extractPrefix(key.rawKey())).isEqualTo(key.keyPrefix());
        assertThat(apiKeyCrypto.extractPrefix("short")).isNull();
        assertThat(apiKeyCrypto.extractPrefix("invalid_prefix_12345678")).isNull();
        assertThat(apiKeyCrypto.extractPrefix(null)).isNull();
    }

    @Test
    @DisplayName("Should throw exception when hashing null key")
    void shouldThrowWhenHashingNull() {
        assertThatThrownBy(() -> apiKeyCrypto.hashKey(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
