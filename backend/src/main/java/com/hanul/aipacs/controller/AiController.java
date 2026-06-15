package com.hanul.aipacs.controller;

import com.hanul.aipacs.domain.StoredArtifactEntity;
import com.hanul.aipacs.dto.AiDtos.AiJobCreateRequest;
import com.hanul.aipacs.dto.AiDtos.AiJobCreateResponse;
import com.hanul.aipacs.dto.AiDtos.AiJobDto;
import com.hanul.aipacs.dto.AiDtos.ResultDicomMetadata;
import com.hanul.aipacs.service.AiJobService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/jobs")
public class AiController {
    private final AiJobService aiJobService;

    public AiController(AiJobService aiJobService) {
        this.aiJobService = aiJobService;
    }

    @PostMapping
    public AiJobCreateResponse create(@RequestBody AiJobCreateRequest request, Authentication authentication) {
        return new AiJobCreateResponse(aiJobService.createJob(request, actor(authentication)));
    }

    @GetMapping
    public List<AiJobDto> latest() {
        return aiJobService.latestJobs();
    }

    @GetMapping("/{jobId}")
    public AiJobDto get(@PathVariable UUID jobId) {
        return aiJobService.getJob(jobId);
    }

    @GetMapping("/{jobId}/overlay.png")
    public ResponseEntity<byte[]> overlay(@PathVariable UUID jobId) {
        StoredArtifactEntity artifact = aiJobService.getArtifact(jobId, "OVERLAY_PNG");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(artifact.getBytes());
    }

    @GetMapping("/{jobId}/heatmap.png")
    public ResponseEntity<byte[]> heatmap(@PathVariable UUID jobId) {
        StoredArtifactEntity artifact = aiJobService.getArtifact(jobId, "HEATMAP_PNG");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(artifact.getBytes());
    }

    @GetMapping("/{jobId}/result-dicom")
    public ResponseEntity<byte[]> resultDicom(@PathVariable UUID jobId) {
        StoredArtifactEntity artifact = aiJobService.getArtifact(jobId, "RESULT_DICOM");
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/dicom"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=hanul-ai-result-" + jobId + ".dcm")
            .body(artifact.getBytes());
    }

    @GetMapping("/{jobId}/result-metadata")
    public ResultDicomMetadata resultMetadata(@PathVariable UUID jobId) {
        return aiJobService.getResultMetadata(jobId);
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
