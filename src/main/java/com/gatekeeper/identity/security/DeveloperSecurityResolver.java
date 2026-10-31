package com.gatekeeper.identity.security;

import com.gatekeeper.common.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeveloperSecurityResolver {

    public static final String AUTHORIZATION_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final String FALLBACK_HEADER = "X-Developer-Id";

    private final JwtService jwtService;

    public DeveloperSecurityResolver(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    /**
     * Resolves the developer ID from either:
     * 1. Authorization: Bearer <jwt>
     * 2. X-Developer-Id header (for backward compatibility)
     */
    public UUID resolveDeveloperId(HttpServletRequest request, UUID fallbackHeaderId) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            JwtClaims claims = jwtService.validateAndExtract(token);
            return claims.developerId();
        }

        if (fallbackHeaderId != null) {
            return fallbackHeaderId;
        }

        String xDevHeader = request.getHeader(FALLBACK_HEADER);
        if (xDevHeader != null && !xDevHeader.isBlank()) {
            try {
                return UUID.fromString(xDevHeader.trim());
            } catch (IllegalArgumentException e) {
                throw new UnauthorizedException("Invalid X-Developer-Id format");
            }
        }

        throw new UnauthorizedException("Authentication required: provide a valid Bearer token or X-Developer-Id header");
    }
}
