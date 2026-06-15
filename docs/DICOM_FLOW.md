# DICOM 흐름

## QIDO-RS

Studies 페이지는 Spring gateway를 호출합니다. Gateway는 Orthanc DICOMweb QIDO-RS를 호출해 studies, series, instances를 검색합니다.

## WADO-RS

Study detail 페이지는 metadata와 preview를 요청합니다. Gateway는 WADO-RS로 Orthanc에서 선택된 DICOM instance를 가져오고, audit event를 기록한 뒤 PNG rendering을 위해 DICOM bytes를 AI service로 전달합니다.

## QC Gate

Inference 전에 gateway는 원본 DICOM bytes를 가져와 필수 UID, transfer syntax, 익명화된 identifier, pixel data, PHI-like string을 검증합니다.

## STOW-RS

Inference 이후 AI service는 생성된 Secondary Capture DICOM 객체를 반환합니다. Gateway는 이를 artifact로 저장하고, STOW-RS를 사용해 Orthanc에 다시 업로드하며, upload 결과를 AI job과 audit log에 기록합니다.

## Read-back Verification

STOW-RS 업로드가 성공하면 Gateway는 생성된 `StudyInstanceUID`, `SeriesInstanceUID`, `SOPInstanceUID`로 Orthanc를 다시 조회합니다. 조회가 성공하면 AI job은 `COMPLETED_VERIFIED`가 되고, 실패하면 `COMPLETED_UNVERIFIED`와 read-back 오류가 기록됩니다.

원본 DICOM 객체는 절대 수정하지 않습니다.
