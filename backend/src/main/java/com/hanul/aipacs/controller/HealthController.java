package com.hanul.aipacs.controller;

import com.hanul.aipacs.dto.DashboardDto;
import com.hanul.aipacs.service.DashboardService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final DashboardService dashboardService;

    public HealthController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/health/full")
    public Map<String, String> fullHealth() {
        return dashboardService.health();
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard() {
        return dashboardService.dashboard();
    }
}
