package com.hanul.aipacs.dto;

import java.util.Map;

public final class StudyDtos {
    private StudyDtos() {
    }

    public record StudySummary(
        String patientId,
        String studyDate,
        String modality,
        String studyDescription,
        String studyInstanceUid,
        String numberOfSeries,
        String aiStatus,
        String qcStatus
    ) {
    }

    public record SeriesSummary(
        String studyInstanceUid,
        String seriesInstanceUid,
        String modality,
        String seriesDescription,
        String numberOfInstances
    ) {
    }

    public record InstanceSummary(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid,
        String sopClassUid,
        String instanceNumber
    ) {
    }

    public record InstanceMetadata(Map<String, Object> tags) {
    }
}
