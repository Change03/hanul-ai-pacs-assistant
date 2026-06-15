package com.hanul.aipacs.dto;

import com.hanul.aipacs.domain.QcStatus;
import com.hanul.aipacs.domain.Severity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class QcDtos {
    private QcDtos() {
    }

    public record QcValidateRequest(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid
    ) {
    }

    public record QcCheckDto(
        String category,
        String name,
        Severity severity,
        boolean passed,
        String message,
        String observed,
        String expectedHint,
        String suggestedFix
    ) {
    }

    public record QcReportDto(
        UUID id,
        QcStatus status,
        List<QcCheckDto> checks,
        Instant createdAt
    ) {
    }
}
