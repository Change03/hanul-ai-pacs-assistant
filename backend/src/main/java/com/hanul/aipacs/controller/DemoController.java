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
            "datasetVersion", "2026.06.demo.v2",
            "generatedAt", Instant.now().toString(),
            "policy", "Only upload=true cases are uploaded to Orthanc by default. Negative cases stay local for QC demos.",
            "cases", List.of(
                valid("ANON001_normal_like", "normal_like", "ANON001", "PASS", "No acute finding demo", "LOW_DEMO"),
                valid("ANON002_bright_opacity_demo", "bright_opacity_demo", "ANON002", "PASS", "Opacity demo", "HIGH_DEMO"),
                valid("ANON003_low_contrast", "low_contrast", "ANON003", "PASS", "Low confidence demo", "LOW_DEMO"),
                valid("ANON004_missing_optional_tag", "missing_optional_tag", "ANON004", "WARN", "No acute finding demo", "LOW_DEMO"),
                valid("ANON005_normal_followup", "normal_like", "ANON005", "PASS", "No acute finding demo", "LOW_DEMO"),
                localOnly("NEG001_phi_like_negative_case", "phi_like_negative_case", "REAL123", "FAIL", "PHI-like identifiers are intentionally local-only."),
                localOnly("NEG002_missing_pixeldata_negative_case", "missing_pixeldata_negative_case", "ANON901", "FAIL", "PixelData is intentionally omitted."),
                localOnly("NEG003_corrupted_invalid_file", "corrupted_invalid_file", "", "FAIL", "This file is intentionally not a DICOM object.")
            )
        );
    }

    private static Map<String, Object> valid(String caseId, String caseType, String patientId, String qc, String finding, String risk) {
        return Map.of(
            "caseId", caseId,
            "caseType", caseType,
            "patientId", patientId,
            "expectedQcStatus", qc,
            "expectedAiFinding", finding,
            "expectedRiskBand", risk,
            "upload", true,
            "notes", "Seeded synthetic DICOM available in Orthanc."
        );
    }

    private static Map<String, Object> localOnly(String caseId, String caseType, String patientId, String qc, String notes) {
        return Map.of(
            "caseId", caseId,
            "caseType", caseType,
            "patientId", patientId,
            "expectedQcStatus", qc,
            "expectedAiFinding", "BLOCKED_BY_QC",
            "expectedRiskBand", "N/A",
            "upload", false,
            "notes", notes
        );
    }
}
