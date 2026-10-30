package com.gatekeeper.plans.repository;

import com.gatekeeper.plans.domain.UsagePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsagePlanRepository extends JpaRepository<UsagePlan, UUID> {
    Optional<UsagePlan> findByName(String name);
    boolean existsByName(String name);
}
