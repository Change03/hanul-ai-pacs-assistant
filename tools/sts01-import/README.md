# STS01 Import

원본 `STS01` PACS 덤프(압축 DICOM, 실환자형 ID, 다양한 모달리티)에서 데모에
적합한 **단일 프레임 grayscale** 인스턴스를 골라, **비압축(Explicit VR Little
Endian)으로 변환 + 재익명화**하여 seed 샘플 디렉터리에 넣는 도구입니다.

이 전처리가 필요한 이유는 데모 QC 게이트가 의도적으로 좁기 때문입니다.

- STS01의 약 99.7%가 압축 전송 구문(JPEG Lossless/Baseline/JPEG 2000)인데, QC는
  Explicit VR Little Endian만 통과시킵니다.
- PatientID가 실환자형(`MS0006`, `snuh_...`, `TEST-27-2`)이라 QC의 `ANON###`
  익명화 검사·PHI 검사를 통과하지 못합니다.
- 일부 원본 UID에 선행 0 컴포넌트가 있어 백엔드 `UidUtil`이 거부합니다.

스크립트는 위 세 가지를 모두 해결해 QC를 `PASS`로 만들고 전체 AI 파이프라인이
동작하도록 합니다. 변환 결과는 `tools/seed-dicoms/samples/user-provided/`에
`STS01_<MODALITY>_NNN.dcm` 형태로 저장되고, 평소 seed 흐름으로 업로드됩니다.

## 전제

`STS01` 폴더가 이 저장소(`hanul-ai-pacs-assistant`)와 같은 상위 폴더에 있어야
합니다. (예: `의료API/STS01`, `의료API/hanul-ai-pacs-assistant`)

## 1) 큐레이션 + 변환 (저장소 루트에서 실행)

```bash
docker run --rm \
  -v "$(pwd)/../STS01:/data:ro" \
  -v "$(pwd)/tools/seed-dicoms/samples/user-provided:/out" \
  -v "$(pwd)/tools/sts01-import:/work:ro" \
  python:3.12-slim sh -c \
  "pip install -r /work/requirements.txt && python /work/prepare_sts01.py"
```

출력 끝에 `validation: N/N clean`이 보이면 모두 QC 통과 가능한 상태입니다.

### 모달리티별 개수 조정 (선택)

```bash
  ... -e STS01_TARGETS="CR=6,DR=2,CT=8,MR=4,XA=1,US=1" ...
```

경로 override: `STS01_SRC`(기본 `/data`), `STS01_OUT`(기본 `/out`).

## 2) Orthanc에 업로드

```bash
docker compose build seed-dicoms
docker compose run --rm seed-dicoms
```

> 참고: seed-dicoms 컨테이너 CMD는 `--replace-existing`이라 업로드 전에 Orthanc의
> 기존 study를 모두 삭제하고 `samples/user-provided`의 전체 `*.dcm`(데모 기본
> 샘플 + STS01 변환본)을 다시 올립니다.

## 3) 확인

```bash
make up                       # 전체 스택 (web 포함) 기동
# http://localhost:3000  로그인 demo / demo  → STS01 study 확인 → QC → AI 분석
```
