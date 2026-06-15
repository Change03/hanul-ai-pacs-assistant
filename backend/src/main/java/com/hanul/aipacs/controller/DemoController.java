package com.hanul.aipacs.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {
    @GetMapping("/manifest")
    public Map<String, Object> manifest() {
        return Map.of(
            "datasetVersion", "2026.06.user-provided.v1",
            "generatedAt", Instant.now().toString(),
            "policy", "Only bundled anonymized user-provided DICOM files are uploaded. Synthetic image samples are no longer generated.",
            "cases", List.of(
                sample("CR_breast_001", "breast_cr", "ANON101", "BREAST CR DEMO", "RCC", "CR", "Breast CR demo image"),
                sample("CR_breast_002", "breast_cr", "ANON101", "BREAST CR DEMO", "RMLO", "CR", "Breast CR demo image"),
                sample("CT_brain_001", "brain_ct", "ANON102", "BRAIN CT DEMO", "Brain pre  4.8  H31s", "CT", "Brain CT demo image"),
                sample("CT_brain_002", "brain_ct", "ANON102", "BRAIN CT DEMO", "Brain pre  4.8  H31s", "CT", "Brain CT demo image"),
                sample("CT_brain_003", "brain_ct", "ANON102", "BRAIN CT DEMO", "Brain pre  4.8  H31s", "CT", "Brain CT demo image"),
                sample("CT_brain_004", "brain_ct", "ANON102", "BRAIN CT DEMO", "Brain pre  4.8  H31s", "CT", "Brain CT demo image"),
                sample("CT_brain_implicit", "brain_ct", "ANON102", "BRAIN CT DEMO", "Brain pre  4.8  H31s", "CT", "Brain CT demo image")
            )
        );
    }

    private static Map<String, Object> sample(
        String caseId,
        String caseType,
        String patientId,
        String studyDescription,
        String seriesDescription,
        String modality,
        String finding
    ) {
        return Map.ofEntries(
            Map.entry("caseId", caseId),
            Map.entry("caseType", caseType),
            Map.entry("patientId", patientId),
            Map.entry("studyDescription", studyDescription),
            Map.entry("seriesDescription", seriesDescription),
            Map.entry("modality", modality),
            Map.entry("expectedQcStatus", "PASS"),
            Map.entry("expectedAiFinding", finding),
            Map.entry("expectedRiskBand", "DEMO_ONLY"),
            Map.entry("upload", true),
            Map.entry("notes", "Anonymized user-provided DICOM bundled with the repository.")
        );
    }
}
