# 보안과 개인정보 보호

이 repository는 로컬 연구/데모용 애플리케이션입니다.

## 데모 로그인

Web app은 로컬 데모 계정을 사용합니다.

- Username: `demo`
- Password: `demo`

Spring Security가 로컬 세션을 관리합니다. 이 방식은 로컬 포트폴리오 데모에는 적합하지만 production용 인증 방식은 아닙니다.

## Roles

Domain model은 다음 role을 지원합니다.

- `ADMIN`
- `RADIOLOGIST_DEMO`
- `ENGINEER`

Seeded `demo` 사용자는 `RADIOLOGIST_DEMO` role을 사용합니다.

## 개인정보 보호 제어

- 실제 환자 데이터는 포함하지 않습니다.
- Seeded DICOM 파일은 `ANON001`, `ANON^DEMO001`, synthetic metadata를 사용합니다.
- QC check는 익명화되지 않은 `PatientID` 값을 차단합니다.
- AI 결과 화면에는 `Demo only. Not for clinical use. No real patient data.` 문구가 표시됩니다.
- Audit log는 접근과 처리 이벤트를 기록합니다.

## Production 적용 시 보완점

- Demo auth를 enterprise identity로 교체해야 합니다.
- HTTPS와 secure cookie 설정을 적용해야 합니다.
- Endpoint별 formal RBAC 검사를 추가해야 합니다.
- Full de-identification profile 기반 DICOM 비식별화 검증을 추가해야 합니다.
- Artifact storage와 database volume 암호화를 적용해야 합니다.
