package com.gatekeeper.apikey.service;

import com.gatekeeper.apikey.crypto.ApiKeyCrypto;
import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.domain.ApiKeyStatus;
import com.gatekeeper.apikey.dto.ApiKeyCreatedResponse;
import com.gatekeeper.apikey.dto.ApiKeyResponse;
import com.gatekeeper.apikey.dto.CreateApiKeyRequest;
import com.gatekeeper.apikey.exception.ApiKeyNotFoundException;
import com.gatekeeper.apikey.repository.ApiKeyRepository;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.exception.DeveloperNotFoundException;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.exception.UsagePlanNotFoundException;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private DeveloperRepository developerRepository;

    @Mock
    private UsagePlanRepository usagePlanRepository;

    private ApiKeyCrypto apiKeyCrypto;
    private ApiKeyService apiKeyService;

    private Developer developer;
    private UsagePlan plan;

    @BeforeEach
    void setUp() {
        apiKeyCrypto = new ApiKeyCrypto();
        apiKeyService = new ApiKeyService(apiKeyRepository, developerRepository, usagePlanRepository, apiKeyCrypto);

        developer = new Developer(UUID.randomUUID(), "dev@gatekeeper.dev", "hash", "ROLE_DEVELOPER", Instant.now());
        plan = new UsagePlan(UUID.randomUUID(), "Standard", 100, 60, Instant.now());
    }

    @Test
    @DisplayName("Should create API key, store only hash, and return raw key in response")
    void shouldCreateApiKeySafely() {
        when(developerRepository.findById(developer.getId())).thenReturn(Optional.of(developer));
        when(usagePlanRepository.findById(plan.getId())).thenReturn(Optional.of(plan));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateApiKeyRequest request = new CreateApiKeyRequest("My Service Key", plan.getId(), null);
        ApiKeyCreatedResponse response = apiKeyService.createApiKey(developer.getId(), request);

        assertThat(response.rawKey()).isNotBlank();
        assertThat(response.rawKey()).startsWith(ApiKeyCrypto.PREFIX_CONSTANT);
        assertThat(response.status()).isEqualTo(ApiKeyStatus.ACTIVE);
        assertThat(response.name()).isEqualTo("My Service Key");

        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        verify(apiKeyRepository).save(captor.capture());
        ApiKey saved = captor.getValue();

        // Database entity stores only hash, NEVER the raw key
        assertThat(saved.getKeyHash()).isNotEqualTo(response.rawKey());
        assertThat(saved.getKeyHash()).hasSize(64);
        assertThat(saved.getKeyPrefix()).isEqualTo(response.keyPrefix());
        assertThat(saved.getStatus()).isEqualTo(ApiKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw DeveloperNotFoundException when developer does not exist")
    void shouldThrowWhenDeveloperNotFound() {
        UUID unknownDevId = UUID.randomUUID();
        when(developerRepository.findById(unknownDevId)).thenReturn(Optional.empty());

        CreateApiKeyRequest request = new CreateApiKeyRequest("Key", plan.getId(), null);
        assertThatThrownBy(() -> apiKeyService.createApiKey(unknownDevId, request))
                .isInstanceOf(DeveloperNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw UsagePlanNotFoundException when plan does not exist")
    void shouldThrowWhenPlanNotFound() {
        when(developerRepository.findById(developer.getId())).thenReturn(Optional.of(developer));
        UUID unknownPlanId = UUID.randomUUID();
        when(usagePlanRepository.findById(unknownPlanId)).thenReturn(Optional.empty());

        CreateApiKeyRequest request = new CreateApiKeyRequest("Key", unknownPlanId, null);
        assertThatThrownBy(() -> apiKeyService.createApiKey(developer.getId(), request))
                .isInstanceOf(UsagePlanNotFoundException.class);
    }

    @Test
    @DisplayName("Should revoke active API key")
    void shouldRevokeApiKey() {
        ApiKey apiKey = new ApiKey(UUID.randomUUID(), developer, plan, "Key", "gk_live_12345678", "hash", null);
        when(apiKeyRepository.findByIdWithDetails(apiKey.getId())).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        apiKeyService.revokeApiKey(developer.getId(), apiKey.getId());

        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(apiKey.getRevokedAt()).isNotNull();
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("Should rotate API key: revoke old key and return newly generated key")
    void shouldRotateApiKey() {
        ApiKey oldKey = new ApiKey(UUID.randomUUID(), developer, plan, "Primary Key", "gk_live_11111111", "old_hash", null);
        when(apiKeyRepository.findByIdWithDetails(oldKey.getId())).thenReturn(Optional.of(oldKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyCreatedResponse response = apiKeyService.rotateApiKey(developer.getId(), oldKey.getId());

        assertThat(oldKey.getStatus()).isEqualTo(ApiKeyStatus.REVOKED);
        assertThat(response.rawKey()).isNotBlank();
        assertThat(response.rawKey()).startsWith(ApiKeyCrypto.PREFIX_CONSTANT);
        assertThat(response.name()).isEqualTo("Primary Key (Rotated)");

        // 2 saves: 1 for old key revoked, 1 for new key created
        verify(apiKeyRepository, times(2)).save(any(ApiKey.class));
    }

    @Test
    @DisplayName("Should validate valid raw API key and return matching active entity")
    void shouldValidateValidKey() {
        ApiKeyCrypto.GeneratedKey generated = apiKeyCrypto.generateKey();
        ApiKey apiKey = new ApiKey(UUID.randomUUID(), developer, plan, "Active Key",
                generated.keyPrefix(), generated.keyHash(), null);

        when(apiKeyRepository.findByKeyPrefixAndStatus(generated.keyPrefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(List.of(apiKey));

        Optional<ApiKey> validated = apiKeyService.validateAndGetApiKey(generated.rawKey());

        assertThat(validated).isPresent();
        assertThat(validated.get().getId()).isEqualTo(apiKey.getId());
    }

    @Test
    @DisplayName("Should reject and expire key when expired")
    void shouldRejectExpiredKey() {
        ApiKeyCrypto.GeneratedKey generated = apiKeyCrypto.generateKey();
        Instant expiredTime = Instant.now().minus(1, ChronoUnit.HOURS);
        ApiKey apiKey = new ApiKey(UUID.randomUUID(), developer, plan, "Expired Key",
                generated.keyPrefix(), generated.keyHash(), expiredTime);

        when(apiKeyRepository.findByKeyPrefixAndStatus(generated.keyPrefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(List.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<ApiKey> validated = apiKeyService.validateAndGetApiKey(generated.rawKey());

        assertThat(validated).isEmpty();
        assertThat(apiKey.getStatus()).isEqualTo(ApiKeyStatus.EXPIRED);
        verify(apiKeyRepository).save(apiKey);
    }

    @Test
    @DisplayName("Should return empty when raw key has invalid prefix or does not match hash")
    void shouldRejectInvalidKey() {
        assertThat(apiKeyService.validateAndGetApiKey("invalid_key")).isEmpty();

        ApiKeyCrypto.GeneratedKey generated = apiKeyCrypto.generateKey();
        when(apiKeyRepository.findByKeyPrefixAndStatus(generated.keyPrefix(), ApiKeyStatus.ACTIVE))
                .thenReturn(List.of());

        assertThat(apiKeyService.validateAndGetApiKey(generated.rawKey())).isEmpty();
    }
}
