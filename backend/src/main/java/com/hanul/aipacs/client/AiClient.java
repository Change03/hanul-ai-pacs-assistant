package com.hanul.aipacs.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.dto.AiDtos.AiInferResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiClient {
    private final RestClient restClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AiClient(RestClient.Builder builder, @Value("${app.ai-service.url}") String baseUrl, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restClient = builder.clone().baseUrl(baseUrl).build();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> health() {
        return restClient.get().uri("/health").retrieve().body(Map.class);
    }

    public byte[] renderPreview(byte[] dicomBytes, String windowPreset) {
        // gateway는 Orthanc에서 받은 DICOM bytes를 raw body로 전달해 multipart 재포장을 피한다.
        return postDicom("/render-preview-raw", dicomBytes, normalizedWindow(windowPreset, "auto"));
    }

    public AiInferResponse infer(byte[] dicomBytes, String windowPreset) {
        // AI 서비스 응답은 DTO로 역직렬화해 job 결과, artifact 저장, audit 로그에서 재사용한다.
        byte[] response = postDicom("/infer-raw", dicomBytes, normalizedWindow(windowPreset, "chest"));
        try {
            return objectMapper.readValue(response, AiInferResponse.class);
        } catch (Exception ex) {
            throw new IllegalStateException("AI service returned an unreadable inference response", ex);
        }
    }

    private byte[] postDicom(String path, byte[] dicomBytes, String windowPreset) {
        // RestClient는 multipart/JSON에는 편하지만 raw byte body는 JDK HttpClient가 더 단순하고 예측 가능하다.
        HttpRequest request = HttpRequest.newBuilder(aiUri(path, windowPreset))
            .timeout(Duration.ofSeconds(120))
            .version(HttpClient.Version.HTTP_1_1)
            .header("Content-Type", "application/dicom")
            .POST(HttpRequest.BodyPublishers.ofByteArray(dicomBytes))
            .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AI service returned " + response.statusCode() + ": " + new String(response.body(), StandardCharsets.UTF_8));
            }
            return response.body();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI service request was interrupted", ex);
        } catch (IOException ex) {
            throw new IllegalStateException("AI service request failed", ex);
        }
    }

    private URI aiUri(String path, String windowPreset) {
        String encodedWindow = URLEncoder.encode(windowPreset, StandardCharsets.UTF_8);
        return URI.create(baseUrl + path + "?windowPreset=" + encodedWindow);
    }

    private static String normalizedWindow(String windowPreset, String fallback) {
        return windowPreset == null || windowPreset.isBlank() ? fallback : windowPreset;
    }
}
