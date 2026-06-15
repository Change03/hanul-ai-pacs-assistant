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
            "disclaimer", "데모 전용입니다. 임상 진료에 사용하지 마세요. 실제 환자 데이터는 없습니다. Demo only. Not for clinical use. No real patient data.",
            "dicomwebRoot", "/dicom-web",
            "mermaid", """
                flowchart LR
                  Web[Next.js 웹] --> Gateway[Spring Boot 게이트웨이]
                  Gateway --> Orthanc[Orthanc DICOMweb]
                  Gateway --> AI[FastAPI AI 서비스]
                  AI --> Result[생성된 Secondary Capture DICOM]
                  Result --> Gateway
                  Gateway -->|STOW-RS| Orthanc
                  Gateway --> Postgres[(PostgreSQL)]
                """,
            "sequenceMermaid", """
                sequenceDiagram
                  participant Web as Next.js Web
                  participant API as Spring Gateway
                  participant PACS as Orthanc DICOMweb
                  participant AI as FastAPI AI Service
                  participant DB as PostgreSQL

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
                "QIDO-RS", "HTTP로 DICOM 검사, 시리즈, 인스턴스를 검색합니다.",
                "WADO-RS", "HTTP로 DICOM 객체 또는 렌더링 데이터를 가져옵니다.",
                "STOW-RS", "생성된 DICOM 객체를 HTTP로 PACS에 다시 저장합니다."
            )
        );
    }
}
