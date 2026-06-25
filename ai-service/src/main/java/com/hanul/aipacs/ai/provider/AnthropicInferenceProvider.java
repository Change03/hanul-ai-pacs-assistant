package com.hanul.aipacs.ai.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.ai.domain.BoundingBox;
import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.domain.ProviderResult;
import com.hanul.aipacs.ai.util.ImageUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class AnthropicInferenceProvider implements InferenceProvider {
    private static final List<String> ALLOWED_LABELS = List.of(
        "Opacity demo",
        "Low confidence demo",
        "No acute finding demo"
    );

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String model;

    public AnthropicInferenceProvider(RestClient restClient, String apiKey, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required when AI_PROVIDER=ANTHROPIC");
        }
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String name() {
        return "ANTHROPIC";
    }

    @Override
    public ProviderResult infer(ProcessedDicom processedDicom) {
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("task", "Classify this demo medical image for a PACS portfolio application.");
        prompt.put("allowedLabels", ALLOWED_LABELS);
        prompt.put("rules", List.of(
            "Return JSON only.",
            "This is not for clinical diagnosis.",
            "Do not claim certainty or provide treatment advice.",
            "Use Low confidence demo if the image is outside scope or uncertain.",
            "Leave boxes empty unless you are certain about pixel coordinates."
        ));
        prompt.put("metadata", Map.of(
            "modality", String.valueOf(processedDicom.dataset().getString(org.dcm4che3.data.Tag.Modality, "")),
            "rows", processedDicom.metadata().get("rows"),
            "columns", processedDicom.metadata().get("columns"),
            "windowCenter", processedDicom.metadata().get("windowCenter"),
            "windowWidth", processedDicom.metadata().get("windowWidth")
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", 400);
        payload.put("temperature", 0);
        payload.put("system", "You are a cautious demo-only medical imaging assistant. You must not provide clinical diagnosis, triage, treatment, or patient-specific advice.");
        payload.put("messages", List.of(Map.of(
            "role", "user",
            "content", List.of(
                Map.of(
                    "type", "image",
                    "source", Map.of(
                        "type", "base64",
                        "media_type", "image/png",
                        "data", ImageUtils.toBase64Png(processedDicom.image(), processedDicom.columns(), processedDicom.rows())
                    )
                ),
                Map.of("type", "text", "text", writeJson(prompt))
            )
        )));

        Map<String, Object> response = restClient.post()
            .uri("/v1/messages")
            .contentType(MediaType.APPLICATION_JSON)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .body(payload)
            .retrieve()
            .body(new ParameterizedTypeReference<>() {
            });
        if (response == null) {
            throw new IllegalStateException("Anthropic response was empty");
        }
        String text = extractText(response);
        Map<String, Object> parsed = parseJsonBlock(text);
        String label = String.valueOf(parsed.getOrDefault("label", parsed.getOrDefault("findingLabel", "Low confidence demo")));
        if (!ALLOWED_LABELS.contains(label)) {
            label = "Low confidence demo";
        }
        double score = boundedScore(parsed.get("score"));
        List<BoundingBox> boxes = parseBoxes(parsed.get("boxes"));
        String rationale = String.valueOf(parsed.getOrDefault("rationale", "")).trim();
        List<String> warnings = new ArrayList<>();
        warnings.add("Anthropic Claude analyzed an anonymized PNG rendering of the DICOM pixels. Demo only. Not for clinical use. No real patient data.");
        if (!rationale.isBlank()) {
            warnings.add("Claude rationale: " + rationale.substring(0, Math.min(240, rationale.length())));
        }
        return new ProviderResult(name(), label, score, boxes, warnings);
    }

    private String extractText(Map<String, Object> response) {
        Object content = response.get("content");
        if (!(content instanceof List<?> blocks)) {
            throw new IllegalStateException("Anthropic response content was missing");
        }
        StringBuilder builder = new StringBuilder();
        for (Object block : blocks) {
            if (block instanceof Map<?, ?> map && "text".equals(map.get("type"))) {
                Object value = map.containsKey("text") ? map.get("text") : "";
                builder.append(String.valueOf(value)).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private Map<String, Object> parseJsonBlock(String text) {
        try {
            return objectMapper.readValue(text, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            int start = text.indexOf('{');
            int end = text.lastIndexOf('}');
            if (start < 0 || end <= start) {
                throw new IllegalStateException("Anthropic response did not contain JSON");
            }
            try {
                return objectMapper.readValue(text.substring(start, end + 1), new TypeReference<>() {
                });
            } catch (Exception ex) {
                throw new IllegalStateException("Anthropic response JSON was unreadable", ex);
            }
        }
    }

    private List<BoundingBox> parseBoxes(Object value) {
        if (!(value instanceof List<?> rawBoxes)) {
            return List.of();
        }
        List<BoundingBox> boxes = new ArrayList<>();
        for (Object rawBox : rawBoxes) {
            if (rawBox instanceof Map<?, ?> box) {
                boxes.add(new BoundingBox(
                    integer(box.get("x")),
                    integer(box.get("y")),
                    integer(box.get("width")),
                    integer(box.get("height")),
                    String.valueOf(box.containsKey("label") ? box.get("label") : "demo"),
                    boundedScore(box.get("score"))
                ));
            }
        }
        return boxes;
    }

    private int integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private double boundedScore(Object value) {
        try {
            double score = Double.parseDouble(String.valueOf(value));
            return Math.round(Math.max(0.0, Math.min(1.0, score)) * 10_000d) / 10_000d;
        } catch (Exception e) {
            return 0.5;
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Anthropic prompt", e);
        }
    }
}
