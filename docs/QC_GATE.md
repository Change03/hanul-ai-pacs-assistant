# QC Gate

QC gate는 포트폴리오 데모를 위해 의도적으로 보수적으로 동작합니다. QC status가 `FAIL`이면 AI inference를 차단합니다.

## 검증 항목

- DICOM Part 10 `DICM` preamble이 존재하는지 확인합니다.
- Gateway demo parser가 explicit VR stream을 파싱할 수 있는지 확인합니다.
- 필수 UID가 존재하고 DICOM UID 문법을 만족하는지 확인합니다.
  - `StudyInstanceUID`
  - `SeriesInstanceUID`
  - `SOPInstanceUID`
  - `SOPClassUID`
- `Modality`가 존재하는지 확인합니다.
- `PatientID`가 존재하고 `ANON###` 형식인지 확인합니다.
- `PixelData`가 존재하는지 확인합니다.
- `TransferSyntaxUID`가 존재하는지 확인합니다.
- 지원하는 Transfer Syntax인지 확인합니다. 데모는 Explicit VR Little Endian을 대상으로 합니다.
- Rows, Columns, BitsAllocated, BitsStored, PhotometricInterpretation을 확인합니다.
- PixelData 길이를 행/열/비트 정보와 대략 비교합니다.
- Private tag 수를 요약합니다.
- BurnedInAnnotation을 확인합니다.
- 선택 항목인 `StudyDescription`, `SeriesDescription` 누락은 warning으로 처리합니다.
- 다음 필드에서 PHI-like string을 검사합니다.
  - `PatientName`
  - `PatientID`
  - `StudyDescription`
  - `SeriesDescription`
  - `InstitutionName`

## 상태

- `PASS`: 실패한 warning 또는 error가 없습니다.
- `WARN`: 선택 항목 또는 주의 항목이 실패했습니다.
- `FAIL`: 필수 안전성 또는 유효성 검증 중 하나 이상이 실패했습니다.

## Report Shape

각 check는 다음 필드를 포함합니다.

- `category`
- `name`
- `severity`
- `passed`
- `message`
- `observed`
- `expectedHint`
- `suggestedFix`
