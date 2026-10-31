package com.gatekeeper.identity.service;

import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.dto.DeveloperResponse;
import com.gatekeeper.identity.dto.RegisterDeveloperRequest;
import com.gatekeeper.identity.exception.DeveloperNotFoundException;
import com.gatekeeper.identity.repository.DeveloperRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeveloperServiceTest {

    @Mock
    private DeveloperRepository developerRepository;

    private DeveloperService developerService;

    @BeforeEach
    void setUp() {
        developerService = new DeveloperService(developerRepository);
    }

    @Test
    @DisplayName("Should register developer with hashed password")
    void shouldRegisterDeveloper() {
        when(developerRepository.existsByEmail("alice@gatekeeper.dev")).thenReturn(false);
        when(developerRepository.save(any(Developer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterDeveloperRequest request = new RegisterDeveloperRequest("alice@gatekeeper.dev", "supersecret123");
        DeveloperResponse response = developerService.registerDeveloper(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("alice@gatekeeper.dev");
        assertThat(response.role()).isEqualTo("ROLE_DEVELOPER");

        ArgumentCaptor<Developer> captor = ArgumentCaptor.forClass(Developer.class);
        verify(developerRepository).save(captor.capture());
        Developer saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("supersecret123");
        assertThat(saved.getPasswordHash()).hasSize(64); // SHA-256
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering already existing email")
    void shouldRejectDuplicateEmail() {
        when(developerRepository.existsByEmail("bob@gatekeeper.dev")).thenReturn(true);

        RegisterDeveloperRequest request = new RegisterDeveloperRequest("bob@gatekeeper.dev", "supersecret123");
        assertThatThrownBy(() -> developerService.registerDeveloper(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email is already registered");
    }

    @Test
    @DisplayName("Should retrieve developer profile by ID")
    void shouldGetDeveloperProfile() {
        UUID devId = UUID.randomUUID();
        Developer dev = new Developer(devId, "carol@gatekeeper.dev", "hash", "ROLE_DEVELOPER", Instant.now());
        when(developerRepository.findById(devId)).thenReturn(Optional.of(dev));

        DeveloperResponse response = developerService.getDeveloper(devId);

        assertThat(response.id()).isEqualTo(devId);
        assertThat(response.email()).isEqualTo("carol@gatekeeper.dev");
    }

    @Test
    @DisplayName("Should throw DeveloperNotFoundException when developer does not exist")
    void shouldThrowWhenDeveloperNotFound() {
        UUID devId = UUID.randomUUID();
        when(developerRepository.findById(devId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> developerService.getDeveloper(devId))
                .isInstanceOf(DeveloperNotFoundException.class);
    }
}
