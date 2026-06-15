# API

Base URL: `http://localhost:8080`

Health API를 제외한 대부분의 API는 `POST /api/auth/login`으로 생성된 로컬 데모 세션을 사용합니다.

## 인증

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

## Health와 Dashboard

- `GET /api/health`
- `GET /api/health/full`
- `GET /api/dashboard`

## Studies와 Instances

- `GET /api/studies`
- `GET /api/studies/{studyInstanceUid}`
- `GET /api/studies/{studyInstanceUid}/series`
- `GET /api/studies/{studyInstanceUid}/instances`
- `GET /api/instances/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}/metadata`
- `GET /api/instances/{studyInstanceUid}/{seriesInstanceUid}/{sopInstanceUid}/preview?window=chest`

지원하는 window preset: `chest`, `lung`, `bone`, `auto`.

## QC

`POST /api/qc/validate`

```json
{
  "studyInstanceUid": "...",
  "seriesInstanceUid": "...",
  "sopInstanceUid": "..."
}
```

응답 예시:

```json
{
  "status": "PASS",
  "checks": [
    {
      "name": "PixelData exists",
      "severity": "ERROR",
      "passed": true,
      "message": "PixelData tag exists",
      "suggestedFix": "Use image storage instances with PixelData."
    }
  ]
}
```

UI는 로컬 corrupt sample 데모를 위해 `POST /api/qc/validate-upload`도 사용합니다.

## AI

`POST /api/ai/jobs`

```json
{
  "studyInstanceUid": "...",
  "seriesInstanceUid": "...",
  "sopInstanceUid": "...",
  "windowPreset": "chest"
}
```

응답:

```json
{"jobId": "..."}
```

Job 조회와 artifact 다운로드:

- `GET /api/ai/jobs/{jobId}`
- `GET /api/ai/jobs/{jobId}/overlay.png`
- `GET /api/ai/jobs/{jobId}/heatmap.png`
- `GET /api/ai/jobs/{jobId}/result-dicom`
- `GET /api/ai/jobs/{jobId}/result-metadata`

`GET /api/ai/jobs/{jobId}`는 다음 안전/검증 필드를 포함합니다.

- `readbackStatus`
- `readbackVerifiedAt`
- `readbackErrorMessage`
- `timeline`
- `disclaimer`
- `clinicalUseAllowed: false`
- `syntheticOnly: true`

## Audit와 Architecture

- `GET /api/audit`
- `GET /api/architecture/runtime`
- `GET /api/demo/manifest`
