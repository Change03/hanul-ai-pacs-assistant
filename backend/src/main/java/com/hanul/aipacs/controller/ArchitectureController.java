package com.hanul.aipacs.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/architecture")
public class ArchitectureController {
    @GetMapping("/runtime")
    public Map<String, Object> runtime() {
        return Map.of(
            "disclaimer", "Demo only. Not for clinical use. No real patient data.",
            "dicomwebRoot", "/dicom-web",
            "mermaid", """
                flowchart LR
                  Web[Next.js Web] --> Gateway[Spring Boot Gateway]
                  Gateway --> Orthanc[Orthanc DICOMweb]
                  Gateway --> AI[Spring Boot AI Service]
                  AI --> Result[Secondary Capture DICOM]
                  Result --> Gateway
                  Gateway -->|STOW-RS| Orthanc
                  Gateway --> Mysql[(MySQL)]
                """,
            "sequenceMermaid", """
                sequenceDiagram
                  participant Web as Next.js Web
                  participant API as Spring Gateway
                  participant PACS as Orthanc DICOMweb
                  participant AI as Spring Boot AI Service
                  participant DB as MySQL

                  Web->>API: Run AI job
                  API->>PACS: WADO-RS retrieve DICOM
                  API->>API: QC Gate
                  API->>AI: infer(DICOM)
                  AI-->>API: overlay, heatmap, result DICOM
                  API->>DB: save job/artifacts
                  API->>PACS: STOW-RS result DICOM
                  API->>PACS: read-back verify
                  API->>DB: audit + timeline
                  Web->>API: get job result
                """,
            "terms", Map.of(
                "QIDO-RS", "HTTP query for DICOM studies, series, and instances.",
                "WADO-RS", "HTTP retrieval for DICOM objects and rendered data.",
                "STOW-RS", "HTTP storage for generated DICOM objects back into PACS."
            )
        );
    }
}
