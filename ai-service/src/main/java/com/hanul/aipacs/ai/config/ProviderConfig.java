package com.hanul.aipacs.ai.config;

import com.hanul.aipacs.ai.provider.AnthropicInferenceProvider;
import com.hanul.aipacs.ai.provider.DemoFallbackProvider;
import com.hanul.aipacs.ai.provider.InferenceProvider;
import com.hanul.aipacs.ai.provider.OnnxInferenceProvider;
import com.hanul.aipacs.ai.provider.ProviderResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ProviderConfig {
    @Bean
    InferenceProvider inferenceProvider(
        @Value("${AI_PROVIDER:}") String providerName,
        @Value("${MODEL_PATH:./models/chest_demo.onnx}") String modelPath,
        @Value("${ANTHROPIC_API_KEY:}") String anthropicApiKey,
        @Value("${ANTHROPIC_MODEL:claude-sonnet-4-5}") String anthropicModel,
        @Value("${ANTHROPIC_BASE_URL:https://api.anthropic.com}") String anthropicBaseUrl
    ) {
        String normalized = providerName == null ? "" : providerName.trim().toUpperCase();
        if ("ANTHROPIC".equals(normalized)) {
            return new AnthropicInferenceProvider(
                RestClient.builder().baseUrl(anthropicBaseUrl).build(),
                anthropicApiKey,
                anthropicModel
            );
        }
        if ("ONNX".equals(normalized)) {
            return new OnnxInferenceProvider(Path.of(modelPath));
        }
        if ("DEMO_FALLBACK".equals(normalized)) {
            return new DemoFallbackProvider();
        }
        if (!normalized.isBlank() && !"AUTO".equals(normalized)) {
            throw new IllegalStateException("AI_PROVIDER must be DEMO_FALLBACK, ONNX, ANTHROPIC, or AUTO");
        }
        if (Files.exists(Path.of(modelPath))) {
            return new OnnxInferenceProvider(Path.of(modelPath));
        }
        return new DemoFallbackProvider();
    }

    @Bean
    ProviderResolver providerResolver(InferenceProvider inferenceProvider) {
        return new ProviderResolver(inferenceProvider);
    }
}
