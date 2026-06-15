# Judge Demo Script

## Prerequisites

- Docker Desktop or Docker Engine
- Optional: Make
- Ports available: 3000, 8080, 8000, 8042, 5432

## Commands

```bash
cp .env.example .env
docker compose up --build
make smoke
```

Without Make:

```bash
docker compose run --rm seed-dicoms
bash scripts/smoke-test.sh
```

## URLs

- Web: http://localhost:3000
- Backend Swagger: http://localhost:8080/swagger-ui.html
- Orthanc: http://localhost:8042

## Credentials

- Web: `demo` / `demo`
- Orthanc: `orthanc` / `orthanc`

## 3-5 Minute Narration

1. Open the web UI and log in with `demo/demo`.
   The judge should see a local demo dashboard with service health and recent audit events.

2. Open `검사 목록`.
   Explain that the studies are synthetic DICOM objects seeded into Orthanc and queried through DICOMweb.

3. Open a seeded study.
   Show the original DICOM preview, WL/WW controls, metadata table, and UID copy buttons. Mention that this is a demo preview renderer, not a diagnostic viewer.

4. Run QC Gate.
   Show PASS/WARN/FAIL checks, categories, observed values, expected hints, and suggested fixes. Explain that QC FAIL blocks AI inference.

5. Run AI Analysis.
   Open the AI job result page. Show original image, overlay, heatmap, score, provider badge, generated UIDs, STOW status, read-back verification badge, QC summary, and timeline.

6. Open Audit Log.
   Show DICOM access, QC validation, AI inference, STOW upload, and Orthanc read-back verification events.

7. Open Architecture.
   Show the flowchart and sequence diagram. Explain QIDO-RS, WADO-RS, and STOW-RS.

## Expected Successful Outcome

- AI job reaches `COMPLETED_VERIFIED`
- STOW status is `STOW_RS_STORED`
- Read-back status is `READBACK_VERIFIED`
- Result DICOM can be downloaded
- Audit log contains QC, AI, STOW, and read-back events

## Fallback Troubleshooting

- No studies: run `docker compose run --rm seed-dicoms`
- Orthanc unavailable: check `docker compose logs orthanc`
- AI job blocked: show QC report; this is expected for FAIL cases
- Read-back failed: check Orthanc logs and generated UIDs
- Login issue: log out and retry `demo/demo`
