package com.gatekeeper.gateway.interceptor;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.service.ApiKeyService;
import com.gatekeeper.gateway.ratelimit.RateLimitResult;
import com.gatekeeper.gateway.ratelimit.RateLimiter;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.service.UsageEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.gatekeeper.observability.GatekeeperMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
public class ApiKeyRateLimitInterceptor implements HandlerInterceptor {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String ATTR_API_KEY = "GATEKEEPER_API_KEY";
    public static final String ATTR_START_TIME = "GATEKEEPER_START_TIME";

    private final ApiKeyService apiKeyService;
    private final RateLimiter rateLimiter;
    private final ObjectProvider<UsageEventService> usageEventServiceProvider;
    private final ObjectProvider<GatekeeperMetrics> metricsProvider;

    @Autowired
    public ApiKeyRateLimitInterceptor(
            ApiKeyService apiKeyService,
            RateLimiter rateLimiter,
            ObjectProvider<UsageEventService> usageEventServiceProvider,
            ObjectProvider<GatekeeperMetrics> metricsProvider) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
        this.usageEventServiceProvider = usageEventServiceProvider;
        this.metricsProvider = metricsProvider;
    }

    public ApiKeyRateLimitInterceptor(
            ApiKeyService apiKeyService,
            RateLimiter rateLimiter,
            ObjectProvider<UsageEventService> usageEventServiceProvider) {
        this(apiKeyService, rateLimiter, usageEventServiceProvider, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        long startTime = System.currentTimeMillis();
        request.setAttribute(ATTR_START_TIME, startTime);

        String rawKey = request.getHeader(API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            recordUsage(null, request.getRequestURI(), 401, System.currentTimeMillis() - startTime,
                    UsageDecision.REJECTED, "Missing X-API-Key header");
            writeErrorResponse(response, HttpStatus.UNAUTHORIZED, "Missing X-API-Key header");
            return false;
        }

        Optional<ApiKey> apiKeyOpt = apiKeyService.validateAndGetApiKey(rawKey);
        if (apiKeyOpt.isEmpty()) {
            recordUsage(null, request.getRequestURI(), 401, System.currentTimeMillis() - startTime,
                    UsageDecision.REJECTED, "Invalid, expired, or revoked API key");
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
            recordUsage(apiKey.getId(), request.getRequestURI(), 429, System.currentTimeMillis() - startTime,
                    UsageDecision.REJECTED, "Rate limit exceeded");

            if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
                metricsProvider.getIfAvailable().recordRateLimitRejected(plan.getName());
            }

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

        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider.getIfAvailable().recordRateLimitAllowed(plan.getName());
        }

        request.setAttribute(ATTR_API_KEY, apiKey);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ApiKey apiKey = (ApiKey) request.getAttribute(ATTR_API_KEY);
        Long startTime = (Long) request.getAttribute(ATTR_START_TIME);
        long latencyMs = startTime != null ? (System.currentTimeMillis() - startTime) : 0L;

        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider.getIfAvailable().recordGatewayLatency(latencyMs);
        }

        if (apiKey != null) {
            recordUsage(apiKey.getId(), request.getRequestURI(), response.getStatus(), latencyMs,
                    UsageDecision.ALLOWED, null);
        }
    }

    private void recordUsage(UUID apiKeyId, String route, int statusCode, long latencyMs,
                             UsageDecision decision, String rejectionReason) {
        UsageEventService service = usageEventServiceProvider.getIfAvailable();
        if (service != null) {
            service.recordEventAsync(apiKeyId, route, statusCode, latencyMs, decision, rejectionReason);
        }
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
