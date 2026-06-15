# Testing Strategy

## Backend

- Unit tests cover UID validation and QC behavior.
- Controller tests cover QC request wiring.
- Smoke test verifies login, DICOM retrieval, QC, AI job execution, STOW-RS, read-back verification, artifact download, and audit log events.

## AI Service

- pytest covers the FastAPI service and DICOM processing behavior.
- DEMO_FALLBACK is deterministic so tests do not require external models or paid APIs.

## Frontend

- Playwright verifies the login shell and route loading.
- When the backend is available, Playwright also logs in with `demo/demo` and navigates core pages.

## Bundled Sample Dataset

- The seed tool generates valid uploadable cases and local-only negative cases.
- `manifest.json` records the bundled anonymized CR/CT samples, expected QC status, and expected demo AI behavior.

## QC Negative Cases

- Corrupted bytes
- PHI-like PatientID/PatientName
- Missing PixelData
- Missing optional description

## STOW/Read-Back Verification

- `make smoke` requires `COMPLETED_VERIFIED`, `STOW_RS_STORED`, `READBACK_VERIFIED`, and matching audit events.

## Not Tested

- Real clinical data
- Compressed transfer syntaxes
- Full DICOM parser conformance
- Real model clinical validity
- Production authentication/authorization controls
