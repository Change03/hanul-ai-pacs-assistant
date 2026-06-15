# DICOM Result Object

## Why the Original DICOM Is Immutable

The original DICOM instance represents the source imaging object in PACS. The demo never modifies it. This makes the AI result auditable and reversible.

## Why a Separate Result Object

AI output is stored as a new DICOM object so the workflow can show provenance:

- source object stays unchanged
- generated UIDs identify derived output
- audit log records when the object was created and stored

## Why Secondary Capture

Secondary Capture Image Storage is used because this demo produces a visual overlay result. It is simple, broadly understood, and enough for a portfolio workflow. It is not a replacement for clinically structured reporting.

## UID Policy

- Same `StudyInstanceUID`
- New `SeriesInstanceUID`
- New `SOPInstanceUID`

## Future SR/SEG Work

- DICOM SR would be better for structured findings and measurements.
- DICOM SEG would be better for segmentation masks.
- This demo keeps those as roadmap items to avoid fake clinical claims.

## ImageComments Summary

`ImageComments` stores a compact JSON summary with provider, score, label, boxes, generated UIDs, and demo-only disclaimer.

## STOW and Read-Back

The backend uploads the generated result DICOM through STOW-RS. After upload, it queries Orthanc again with the generated Study/Series/SOP UIDs. A successful read-back marks the job `COMPLETED_VERIFIED`.
