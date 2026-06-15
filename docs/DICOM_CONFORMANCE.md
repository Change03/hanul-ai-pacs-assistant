# Demo DICOM Conformance Statement

## Project Scope

Hanul AI-PACS Assistant is a local, synthetic-data-only portfolio demo. It demonstrates a narrow DICOMweb workflow with Orthanc, a Spring Boot gateway, a FastAPI AI service, and a Next.js UI. It is not a full hospital PACS, diagnostic viewer, or complete DICOM parser.

## Supported Input DICOM

- Synthetic data only
- Single-frame grayscale demo DICOM
- Preferred transfer syntax: uncompressed Explicit VR Little Endian (`1.2.840.10008.1.2.1`)
- Required identifiers:
  - `StudyInstanceUID`
  - `SeriesInstanceUID`
  - `SOPInstanceUID`
  - `SOPClassUID`
- `PixelData` is required for AI inference
- Rows, Columns, BitsAllocated, BitsStored, PhotometricInterpretation, Modality, PatientID, and TransferSyntaxUID are checked by QC

## Supported Output DICOM

- SOP Class: Secondary Capture Image Storage
- Same `StudyInstanceUID` as the original
- New `SeriesInstanceUID`
- New `SOPInstanceUID`
- `Modality`: `OT`
- `SeriesDescription`: `AI_RESULT_DEMO`
- `ImageComments` contains a compact AI result summary

## DICOMweb Operations

- QIDO-RS for study/series/instance search
- WADO-RS or Orthanc REST for retrieval
- STOW-RS for storing generated result DICOM
- Orthanc read-back verification after STOW-RS

## Unsupported

- Real patient data
- Compressed transfer syntaxes
- JPEG, JPEG-LS, JPEG 2000, RLE
- Multi-frame CT/MR
- Ultrasound multi-frame
- RTSTRUCT
- DICOM SEG input
- DICOM SR input
- Encapsulated PDF

## QC Policy

- `PASS`: no blocking errors or warnings
- `WARN`: no blocking errors, but optional metadata or caution checks failed
- `FAIL`: invalid or unsafe for demo AI inference

Blocking failures include unparseable DICOM, required UID missing or malformed, missing PixelData, unsupported Transfer Syntax, or obvious PHI-like PatientName/PatientID.

## Limitations

The gateway uses a lightweight explicit-VR parser for QC and metadata. Pixel rendering and AI preprocessing use pydicom in the AI service. The web viewer is a demo preview renderer, not a diagnostic viewer.

## Roadmap

- DICOM SR for structured findings
- DICOM SEG for segmentation masks
- Cornerstone3D/OHIF viewer integration
- Richer de-identification profile
