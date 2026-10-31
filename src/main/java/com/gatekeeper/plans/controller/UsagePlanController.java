package com.gatekeeper.plans.controller;

import com.gatekeeper.plans.dto.UsagePlanResponse;
import com.gatekeeper.plans.repository.UsagePlanRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/plans")
public class UsagePlanController {

    private final UsagePlanRepository usagePlanRepository;

    public UsagePlanController(UsagePlanRepository usagePlanRepository) {
        this.usagePlanRepository = usagePlanRepository;
    }

    @GetMapping
    public ResponseEntity<List<UsagePlanResponse>> listPlans() {
        List<UsagePlanResponse> plans = usagePlanRepository.findAll().stream()
                .map(plan -> new UsagePlanResponse(
                        plan.getId(),
                        plan.getName(),
                        plan.getRequestsPerWindow(),
                        plan.getWindowSeconds(),
                        plan.getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(plans);
    }
}
