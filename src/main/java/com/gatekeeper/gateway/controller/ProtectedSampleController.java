package com.gatekeeper.gateway.controller;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.gateway.interceptor.ApiKeyRateLimitInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/protected")
public class ProtectedSampleController {

    @GetMapping("/hello")
    public ResponseEntity<Map<String, Object>> hello(HttpServletRequest request) {
        ApiKey apiKey = (ApiKey) request.getAttribute(ApiKeyRateLimitInterceptor.ATTR_API_KEY);

        Map<String, Object> response = Map.of(
                "message", "Hello! You have successfully accessed a Gatekeeper protected endpoint.",
                "keyName", apiKey.getName(),
                "keyPrefix", apiKey.getKeyPrefix(),
                "planName", apiKey.getPlan().getName(),
                "requestsPerWindow", apiKey.getPlan().getRequestsPerWindow(),
                "windowSeconds", apiKey.getPlan().getWindowSeconds(),
                "timestamp", Instant.now().toString()
        );

        return ResponseEntity.ok(response);
    }
}
