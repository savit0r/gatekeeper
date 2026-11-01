package com.gatekeeper.identity.service;

import com.gatekeeper.common.exception.UnauthorizedException;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.dto.AuthResponse;
import com.gatekeeper.identity.dto.LoginRequest;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.identity.security.JwtService;
import com.gatekeeper.observability.GatekeeperMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final DeveloperRepository developerRepository;
    private final DeveloperService developerService;
    private final JwtService jwtService;
    private final ObjectProvider<GatekeeperMetrics> metricsProvider;

    @Autowired
    public AuthService(DeveloperRepository developerRepository,
                       DeveloperService developerService,
                       JwtService jwtService,
                       ObjectProvider<GatekeeperMetrics> metricsProvider) {
        this.developerRepository = developerRepository;
        this.developerService = developerService;
        this.jwtService = jwtService;
        this.metricsProvider = metricsProvider;
    }

    public AuthService(DeveloperRepository developerRepository,
                       DeveloperService developerService,
                       JwtService jwtService) {
        this(developerRepository, developerService, jwtService, null);
    }

    public AuthResponse login(LoginRequest request) {
        Developer developer = developerRepository.findByEmail(request.email().toLowerCase().trim())
                .orElseThrow(() -> {
                    recordLoginMetric("failure");
                    return new UnauthorizedException("Invalid email or password");
                });

        if (!developerService.verifyPassword(request.password(), developer.getPasswordHash())) {
            recordLoginMetric("failure");
            throw new UnauthorizedException("Invalid email or password");
        }

        recordLoginMetric("success");
        String token = jwtService.generateToken(developer.getId(), developer.getEmail(), developer.getRole());

        return new AuthResponse(
                token,
                "Bearer",
                jwtService.getExpirationSeconds(),
                developer.getId(),
                developer.getEmail(),
                developer.getRole()
        );
    }

    private void recordLoginMetric(String result) {
        if (metricsProvider != null && metricsProvider.getIfAvailable() != null) {
            metricsProvider.getIfAvailable().recordLogin(result);
        }
    }
}
