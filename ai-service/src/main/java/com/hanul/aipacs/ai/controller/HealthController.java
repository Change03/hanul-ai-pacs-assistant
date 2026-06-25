package com.hanul.aipacs.ai.controller;

import com.hanul.aipacs.ai.provider.ProviderResolver;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final ProviderResolver providerResolver;

    public HealthController(ProviderResolver providerResolver) {
        this.providerResolver = providerResolver;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "provider", providerResolver.current().name());
    }
}
