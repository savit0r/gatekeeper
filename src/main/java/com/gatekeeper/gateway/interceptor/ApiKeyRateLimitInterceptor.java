package com.gatekeeper.gateway.interceptor;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.gateway.ratelimit.RateLimitResult;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.plans.domain.UsagePlan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

@Component
public class ApiKeyRateLimitInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ATTR_API_KEY = "GATEKEEPER_API_KEY";
    public static final String ATTR_START_TIME = "GATEKEEPER_START_TIME";

    private final ApiKeyService apiKeyService;
    private final RateLimiter rateLimiter;

    public ApiKeyRateLimitInterceptor(ApiKeyService apiKeyService, RateLimiter rateLimiter) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return false;
        }

        Optional<ApiKey> apiKeyOpt = apiKeyService.validateAndGetApiKey(rawKey);
        if (apiKeyOpt.isEmpty()) {
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Invalid, expired, or revoked API key");
            return false;
        }

        ApiKey apiKey = apiKeyOpt.get();
        UsagePlan plan = apiKey.getPlan();

        RateLimitResult result = rateLimiter.checkRateLimit(
                apiKey.getId(),
                plan.getRequestsPerWindow(),
                plan.getWindowSeconds()
        );

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));

        if (result.isFallback()) {
            response.setHeader("X-RateLimit-Fallback", "true");
        }

        if (!result.allowed()) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(result.resetSeconds()));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            String body = """
                    {
                        "timestamp": "%s",
                        "status": 429,
                        "error": "Too Many Requests",
                        "message": "Rate limit exceeded. Try again in %d seconds.",
                        "limit": %d,
                        "remaining": 0,
                        "resetSeconds": %d
                    }
                    """.formatted(Instant.now(), result.resetSeconds(), result.limit(), result.resetSeconds());

            response.getWriter().write(body);
            return false;
        }

        request.setAttribute(ATTR_API_KEY, apiKey);
        return true;
    }

    private void writeErrorResponse(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = """
                {
                    "timestamp": "%s",
                    "status": %d,
                    "error": "%s",
                    "message": "%s"
                }
                """.formatted(Instant.now(), status.value(), status.getReasonPhrase(), message);
        response.getWriter().write(body);
    }
}
