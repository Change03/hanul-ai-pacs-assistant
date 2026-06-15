# 포트폴리오 발표 자료

## 한 줄 피치

Hanul AI-PACS Assistant는 DICOMweb, Orthanc, Spring Boot, FastAPI, Next.js를 이용해 의료영상 AI의 end-to-end workflow를 보여주는 포트폴리오 프로젝트입니다.

## 심사자가 주목해야 할 점

- 실제 DICOMweb 개념인 QIDO-RS, WADO-RS, STOW-RS를 사용합니다.
- 원본 영상은 변경하지 않습니다.
- AI output은 새로운 DICOM 객체로 저장됩니다.
- QC가 안전하지 않거나 유효하지 않은 입력이 inference로 넘어가는 것을 막습니다.
- 중요한 모든 동작은 audit log로 기록됩니다.
- 실제 환자 데이터나 다운로드된 실제 모델 없이도 실행할 수 있습니다.

## 추천 라이브 데모 흐름

1. Dashboard와 health card에서 시작합니다.
2. Studies를 열고 synthetic metadata를 설명합니다.
3. Opacity case를 선택합니다.
4. QC를 실행하고 PASS/WARN/FAIL을 설명합니다.
5. AI를 실행하고 결과 페이지가 열릴 때까지 기다립니다.
6. Overlay, heatmap, generated UID, STOW-RS status를 보여줍니다.
7. Audit을 열어 event trail을 확인합니다.
8. Architecture를 열고 DICOMweb을 쉬운 말로 설명합니다.

## 알려진 한계

- Java QC parser는 생성된 explicit-VR demo file을 대상으로 하며 full DICOM toolkit은 아닙니다.
- Fallback model은 결정론적 데모 알고리즘이며 진단 모델이 아닙니다.
- Compressed transfer syntax는 이 데모의 주요 대상이 아닙니다.
- Session auth는 local demo 수준입니다.
