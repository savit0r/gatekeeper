package com.gatekeeper.identity.service;

import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.dto.DeveloperResponse;
import com.gatekeeper.identity.dto.RegisterDeveloperRequest;
import com.gatekeeper.identity.exception.DeveloperNotFoundException;
import com.gatekeeper.identity.repository.DeveloperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@Transactional
public class DeveloperService {

    private final DeveloperRepository developerRepository;

    public DeveloperService(DeveloperRepository developerRepository) {
        this.developerRepository = developerRepository;
    }

    public DeveloperResponse registerDeveloper(RegisterDeveloperRequest request) {
        if (developerRepository.existsByEmail(request.email().toLowerCase().trim())) {
            throw new IllegalArgumentException("Email is already registered: " + request.email());
        }

        String passwordHash = hashPassword(request.password());

        Developer developer = new Developer(
                UUID.randomUUID(),
                request.email().toLowerCase().trim(),
                passwordHash,
                "ROLE_DEVELOPER",
                Instant.now()
        );

        Developer saved = developerRepository.save(developer);

        return new DeveloperResponse(
                saved.getId(),
                saved.getEmail(),
                saved.getRole(),
                saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public DeveloperResponse getDeveloper(UUID developerId) {
        Developer developer = developerRepository.findById(developerId)
                .orElseThrow(() -> new DeveloperNotFoundException(developerId));

        return new DeveloperResponse(
                developer.getId(),
                developer.getEmail(),
                developer.getRole(),
                developer.getCreatedAt()
        );
    }

    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        String computedHash = hashPassword(rawPassword);
        byte[] computedBytes = computedHash.getBytes(StandardCharsets.UTF_8);
        byte[] storedBytes = storedHash.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(computedBytes, storedBytes);
    }

    public String hashPassword(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
