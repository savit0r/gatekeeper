package com.gatekeeper.identity.security;

import com.gatekeeper.common.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder B64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();

    private static final String FIXED_HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String ENCODED_HEADER = B64_ENCODER.encodeToString(FIXED_HEADER_JSON.getBytes(StandardCharsets.UTF_8));

    private static final Pattern SUB_PATTERN = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern ROLE_PATTERN = Pattern.compile("\"role\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern EXP_PATTERN = Pattern.compile("\"exp\"\\s*:\\s*(\\d+)");

    private final byte[] secretKeyBytes;
    private final long expirationSeconds;

    public JwtService(
            @Value("${gatekeeper.jwt.secret:gatekeeper-default-super-secret-jwt-signing-key-32bytes-long!}") String secret,
            @Value("${gatekeeper.jwt.expiration-seconds:86400}") long expirationSeconds) {
        this.secretKeyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationSeconds;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    /**
     * Generates a signed JWT with subject, email, role, iat, and exp claims.
     */
    public String generateToken(UUID developerId, String email, String role) {
        Instant now = Instant.now();
        long iat = now.getEpochSecond();
        long exp = now.plusSeconds(expirationSeconds).getEpochSecond();

        String payloadJson = """
                {"sub":"%s","email":"%s","role":"%s","iat":%d,"exp":%d}"""
                .formatted(developerId, email, role, iat, exp);

        String encodedPayload = B64_ENCODER.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String contentToSign = ENCODED_HEADER + "." + encodedPayload;
        String signature = sign(contentToSign);

        return contentToSign + "." + signature;
    }

    /**
     * Validates signature, structure, and expiration; returns parsed claims.
     */
    public JwtClaims validateAndExtract(String token) {
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Missing authentication token");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new UnauthorizedException("Malformed authentication token");
        }

        String headerAndPayload = parts[0] + "." + parts[1];
        String expectedSignature = sign(headerAndPayload);
        String actualSignature = parts[2];

        // Constant-time signature verification
        byte[] expectedBytes = expectedSignature.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actualSignature.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new UnauthorizedException("Invalid token signature");
        }

        // Decode and parse payload
        String payloadJson;
        try {
            payloadJson = new String(B64_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Invalid token payload encoding");
        }

        UUID developerId = extractUuid(payloadJson, SUB_PATTERN, "sub");
        String email = extractString(payloadJson, EMAIL_PATTERN, "email");
        String role = extractString(payloadJson, ROLE_PATTERN, "role");
        long expSeconds = extractLong(payloadJson, EXP_PATTERN, "exp");
        Instant expiresAt = Instant.ofEpochSecond(expSeconds);

        if (Instant.now().isAfter(expiresAt)) {
            throw new UnauthorizedException("Authentication token has expired");
        }

        return new JwtClaims(developerId, email, role, expiresAt);
    }

    private String sign(String content) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_SHA256));
            byte[] signatureBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            return B64_ENCODER.encodeToString(signatureBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to calculate HMAC signature", e);
        }
    }

    private UUID extractUuid(String json, Pattern pattern, String fieldName) {
        String val = extractString(json, pattern, fieldName);
        try {
            return UUID.fromString(val);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Malformed " + fieldName + " in token");
        }
    }

    private String extractString(String json, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new UnauthorizedException("Missing " + fieldName + " claim in token");
        }
        return matcher.group(1);
    }

    private long extractLong(String json, Pattern pattern, String fieldName) {
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            throw new UnauthorizedException("Missing " + fieldName + " claim in token");
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            throw new UnauthorizedException("Malformed " + fieldName + " in token");
        }
    }
}
