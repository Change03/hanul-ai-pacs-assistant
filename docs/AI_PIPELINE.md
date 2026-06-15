# AI 파이프라인

AI service는 pydicom, numpy, pillow, onnxruntime를 사용하는 FastAPI 애플리케이션입니다.

## Provider 선택

심사 기준 provider:

- `DEMO_FALLBACK`: 모델 파일이 없을 때 사용하는 결정론적 fallback입니다.
- `ONNX`: `./models/chest_demo.onnx` 파일이 있을 때 사용합니다.
- `AUTO`: ONNX 모델 파일이 있으면 ONNX, 없으면 fallback을 사용합니다.

실험적 provider:

- `ANTHROPIC`: synthetic PNG만 외부 API로 보내는 실험 옵션입니다. 기본 비활성화이며 심사에 필요하지 않습니다. 실제 환자 데이터에는 사용하지 마세요.

Fallback 사용 여부는 API 응답과 UI에서 의도적으로 명확하게 표시됩니다.

## 전처리

1. pydicom으로 DICOM을 파싱합니다.
2. `pixel_array`를 추출합니다.
3. `RescaleSlope`와 `RescaleIntercept`를 적용합니다.
4. 선택된 WL/WW preset 또는 auto windowing을 적용합니다.
5. 8-bit display image로 정규화합니다.
6. provider inference를 위해 `224x224`로 resize합니다.

## Fallback Inference

Demo provider는 image intensity distribution을 기반으로 그럴듯한 label, score, heatmap, bounding box를 결정론적으로 생성합니다.

- 밝은 국소 영역 -> `Opacity demo`
- 매우 낮은 contrast -> `Low confidence demo`
- 그 외 -> `No acute finding demo`

## 결과 DICOM

Service는 Secondary Capture DICOM을 생성합니다.

- `SOPClassUID`: Secondary Capture Image Storage
- `StudyInstanceUID`: 원본과 동일
- `SeriesInstanceUID`: 새로 생성
- `SOPInstanceUID`: 새로 생성
- `Modality`: `OT`
- `SeriesDescription`: `AI_RESULT_DEMO`
- `PixelData`: RGB overlay
- `ImageComments`: compact JSON summary

안전 고지: 데모 전용입니다. 임상 용도로 사용할 수 없습니다. 실제 환자 데이터는 포함하지 않습니다.

## Job Verification

Backend는 결과 DICOM을 STOW-RS로 Orthanc에 저장한 뒤 read-back 조회를 수행합니다. 성공하면 `COMPLETED_VERIFIED`, 실패하면 `COMPLETED_UNVERIFIED`로 기록합니다.
