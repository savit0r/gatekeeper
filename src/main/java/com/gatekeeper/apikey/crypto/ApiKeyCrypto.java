package com.gatekeeper.apikey.crypto;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class ApiKeyCrypto {

    public static final String PREFIX_CONSTANT = "gk_live_";
    public static final int PREFIX_LENGTH = 16; // "gk_live_" (8 chars) + 8 random hex chars
    private static final int ENTROPY_BYTES = 32; // 256 bits of entropy

    private final SecureRandom secureRandom = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    public record GeneratedKey(String rawKey, String keyPrefix, String keyHash) {}

    /**
     * Generates a new API key with 256 bits of entropy.
     * Format: gk_live_<8_hex_chars>_<64_hex_chars>
     * keyPrefix: gk_live_<8_hex_chars> (16 chars, stored in DB for fast candidate lookup)
     * keyHash: SHA-256 of full rawKey (64 hex chars, stored in DB)
     */
    public GeneratedKey generateKey() {
        byte[] prefixBytes = new byte[4];
        secureRandom.nextBytes(prefixBytes);
        String prefixRandom = HEX.formatHex(prefixBytes);
        String prefix = PREFIX_CONSTANT + prefixRandom;

        byte[] secretBytes = new byte[ENTROPY_BYTES];
        secureRandom.nextBytes(secretBytes);
        String secret = HEX.formatHex(secretBytes);

        String rawKey = prefix + "_" + secret;
        String keyHash = hashKey(rawKey);

        return new GeneratedKey(rawKey, prefix, keyHash);
    }

    /**
     * Computes the SHA-256 hash of the raw API key.
     */
    public String hashKey(String rawKey) {
        if (rawKey == null) {
            throw new IllegalArgumentException("Raw API key must not be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HEX.formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Extracts the lookup prefix (16 chars) from a raw key.
     * Returns null if key is null or too short or does not start with prefix constant.
     */
    public String extractPrefix(String rawKey) {
        if (rawKey == null || rawKey.length() < PREFIX_LENGTH || !rawKey.startsWith(PREFIX_CONSTANT)) {
            return null;
        }
        return rawKey.substring(0, PREFIX_LENGTH);
    }

    /**
     * Performs a constant-time comparison of the incoming key's SHA-256 hash
     * with the stored hash to prevent timing attacks.
     */
    public boolean verifyKey(String rawKey, String storedHash) {
        if (rawKey == null || storedHash == null) {
            return false;
        }
        String computedHash = hashKey(rawKey);
        byte[] computedBytes = computedHash.getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(computedBytes, storedBytes);
    }
}
