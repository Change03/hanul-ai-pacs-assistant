# Sample QC Reports

## PASS

```json
{
  "status": "PASS",
  "checks": [
    {
      "category": "DICOM_STRUCTURE",
      "name": "DICOM 파싱 가능",
      "severity": "ERROR",
      "passed": true,
      "message": "DICOM 태그를 파싱했습니다",
      "observed": "32개 태그",
      "expectedHint": "Explicit VR Little Endian DICOM 태그",
      "suggestedFix": "손상된 파일은 거부하거나 이 데모용 Explicit VR Little Endian으로 변환하세요."
    },
    {
      "category": "PRIVACY",
      "name": "PatientID 익명화",
      "severity": "ERROR",
      "passed": true,
      "message": "PatientID가 ANON### 데모 패턴을 따릅니다",
      "observed": "ANON001",
      "expectedHint": "ANON001 같은 합성 ID",
      "suggestedFix": "식별자를 ANON001 같은 합성 ID로 바꾸세요."
    }
  ]
}
```

## WARN

```json
{
  "status": "WARN",
  "checks": [
    {
      "category": "DICOM_STRUCTURE",
      "name": "StudyDescription 권장",
      "severity": "WARN",
      "passed": false,
      "message": "검사 실패: StudyDescription 권장",
      "observed": "없음",
      "expectedHint": "CHEST DEMO NORMAL 같은 합성 비식별 설명",
      "suggestedFix": "CHEST DEMO NORMAL 같은 합성 비식별 StudyDescription을 추가하세요."
    }
  ]
}
```

## FAIL

```json
{
  "status": "FAIL",
  "checks": [
    {
      "category": "PRIVACY",
      "name": "PatientID 익명화",
      "severity": "ERROR",
      "passed": false,
      "message": "검사 실패: PatientID 익명화",
      "observed": "REAL123",
      "expectedHint": "ANON001 같은 합성 ID",
      "suggestedFix": "식별자를 ANON001 같은 합성 ID로 바꾸세요."
    },
    {
      "category": "PIXEL_INTEGRITY",
      "name": "PixelData 존재",
      "severity": "ERROR",
      "passed": false,
      "message": "검사 실패: PixelData 존재",
      "observed": "PixelData 없음",
      "expectedHint": "AI 입력용 PixelData",
      "suggestedFix": "PixelData가 있는 이미지 저장 인스턴스를 사용하세요."
    }
  ]
}
```

Categories used by the demo: `DICOM_STRUCTURE`, `UID_INTEGRITY`, `PIXEL_INTEGRITY`, `TRANSFER_SYNTAX`, `PRIVACY`, `AI_READINESS`.
