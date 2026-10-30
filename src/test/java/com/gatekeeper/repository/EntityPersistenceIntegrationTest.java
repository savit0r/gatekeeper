package com.gatekeeper.repository;

import com.gatekeeper.apikey.domain.ApiKey;
import com.gatekeeper.apikey.domain.ApiKeyStatus;
import com.gatekeeper.apikey.repository.ApiKeyRepository;
import com.gatekeeper.identity.domain.Developer;
import com.gatekeeper.identity.repository.DeveloperRepository;
import com.gatekeeper.plans.domain.UsagePlan;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import com.gatekeeper.usage.domain.UsageDecision;
import com.gatekeeper.usage.domain.UsageEvent;
import com.gatekeeper.usage.repository.UsageEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class EntityPersistenceIntegrationTest {

    @Autowired
    private DeveloperRepository developerRepository;

    @Autowired
    private UsagePlanRepository usagePlanRepository;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UsageEventRepository usageEventRepository;

    @Test
    @DisplayName("Should persist and retrieve Developer, UsagePlan, ApiKey, and UsageEvent entities successfully")
    void shouldPersistAndRetrieveEntities() {
        // 1. Create Developer
        Developer dev = new Developer(
                UUID.randomUUID(),
                "test-" + UUID.randomUUID() + "@gatekeeper.dev",
                "hashed_password_123",
                "ROLE_DEVELOPER",
                null
        );
        developerRepository.save(dev);

        Optional<Developer> foundDev = developerRepository.findById(dev.getId());
        assertThat(foundDev).isPresent();
        assertThat(foundDev.get().getEmail()).isEqualTo(dev.getEmail());

        // 2. Create UsagePlan
        UsagePlan plan = new UsagePlan(
                UUID.randomUUID(),
                "Starter-" + UUID.randomUUID(),
                60,
                60,
                null
        );
        usagePlanRepository.save(plan);

        Optional<UsagePlan> foundPlan = usagePlanRepository.findById(plan.getId());
        assertThat(foundPlan).isPresent();
        assertThat(foundPlan.get().getRequestsPerWindow()).isEqualTo(60);

        // 3. Create ApiKey
        String prefix = "gk_live_1234";
        String hash = "hash_" + UUID.randomUUID().toString().replace("-", "");
        ApiKey apiKey = new ApiKey(
                UUID.randomUUID(),
                dev,
                plan,
                "Default Key",
                prefix,
                hash,
                null
        );
        apiKeyRepository.save(apiKey);

        List<ApiKey> activeKeys = apiKeyRepository.findByKeyPrefixAndStatus(prefix, ApiKeyStatus.ACTIVE);
        assertThat(activeKeys).hasSize(1);
        assertThat(activeKeys.getFirst().getKeyHash()).isEqualTo(hash);

        // 4. Create UsageEvent
        UsageEvent event = new UsageEvent(
                UUID.randomUUID(),
                apiKey,
                "/api/v1/protected/hello",
                200,
                15L,
                UsageDecision.ALLOWED,
                null
        );
        usageEventRepository.save(event);

        List<UsageEvent> events = usageEventRepository.findByApiKeyIdOrderByOccurredAtDesc(apiKey.getId());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getRoute()).isEqualTo("/api/v1/protected/hello");
        assertThat(events.getFirst().getDecision()).isEqualTo(UsageDecision.ALLOWED);
    }
}
