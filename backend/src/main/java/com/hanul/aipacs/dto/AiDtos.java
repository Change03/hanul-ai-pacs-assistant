package com.hanul.aipacs.dto;

import com.hanul.aipacs.domain.enums.AiJobStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiDtos {
    private AiDtos() {
    }

    public record AiJobCreateRequest(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid,
        String windowPreset
    ) {
    }

    public record AiJobCreateResponse(UUID jobId) {
    }

    public record AiJobEventDto(
        UUID id,
        String eventType,
        String status,
        String message,
        Map<String, Object> details,
        Instant createdAt
    ) {
    }

    public record ResultDicomMetadata(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid,
        String sopClassUid,
        String modality,
        String seriesDescription,
        String imageComments,
        String rows,
        String columns,
        String transferSyntaxUid
    ) {
    }

    public record BoxDto(
        int x,
        int y,
        int width,
        int height,
        String label,
        double score
    ) {
    }

    public record AiInferResponse(
        String modelProvider,
        String findingLabel,
        double score,
        List<BoxDto> boxes,
        String heatmapPngBase64,
        String overlayPngBase64,
        String resultDicomBase64,
        Map<String, Object> preprocessing,
        List<String> warnings
    ) {
    }

    public record AiJobDto(
        UUID id,
        AiJobStatus status,
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid,
        String resultSeriesInstanceUid,
        String resultSopInstanceUid,
        String modelProvider,
        String findingLabel,
        Double score,
        String qcStatus,
        String stowStatus,
        String readbackStatus,
        Instant readbackVerifiedAt,
        String readbackErrorMessage,
        String errorMessage,
        Map<String, Object> result,
        List<AiJobEventDto> timeline,
        String disclaimer,
        boolean clinicalUseAllowed,
        boolean syntheticOnly,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
