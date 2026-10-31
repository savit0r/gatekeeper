package com.gatekeeper.identity.security;

import com.gatekeeper.common.exception.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-at-least-256-bits-long-for-hmac-sha256!", 3600);
    }

    @Test
    @DisplayName("generateToken and validateAndExtract returns valid claims")
    void shouldGenerateAndValidateToken() {
        UUID devId = UUID.randomUUID();
        String email = "dev@gatekeeper.dev";
        String role = "ROLE_DEVELOPER";

        String token = jwtService.generateToken(devId, email, role);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        JwtClaims claims = jwtService.validateAndExtract(token);

        assertThat(claims.developerId()).isEqualTo(devId);
        assertThat(claims.email()).isEqualTo(email);
        assertThat(claims.role()).isEqualTo(role);
        assertThat(claims.isExpired()).isFalse();
    }

    @Test
    @DisplayName("validateAndExtract rejects token with tampered signature")
    void shouldRejectTamperedSignature() {
        UUID devId = UUID.randomUUID();
        String token = jwtService.generateToken(devId, "tamper@gatekeeper.dev", "ROLE_DEVELOPER");

        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + ".tamperedSignature123";

        assertThatThrownBy(() -> jwtService.validateAndExtract(tamperedToken))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid token signature");
    }

    @Test
    @DisplayName("validateAndExtract rejects expired token")
    void shouldRejectExpiredToken() {
        // JwtService configured with negative expiration
        JwtService expiredService = new JwtService("test-secret-key-at-least-256-bits-long-for-hmac-sha256!", -10);

        UUID devId = UUID.randomUUID();
        String token = expiredService.generateToken(devId, "expired@gatekeeper.dev", "ROLE_DEVELOPER");

        assertThatThrownBy(() -> expiredService.validateAndExtract(token))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("has expired");
    }

    @Test
    @DisplayName("validateAndExtract rejects malformed token")
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> jwtService.validateAndExtract("not-a-jwt"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Malformed");

        assertThatThrownBy(() -> jwtService.validateAndExtract(""))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Missing");
    }
}
