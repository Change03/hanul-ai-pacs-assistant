#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
AI_SERVICE_URL="${AI_SERVICE_URL:-http://localhost:8000}"
ORTHANC_URL="${ORTHANC_URL:-http://localhost:8042}"
ORTHANC_USERNAME="${ORTHANC_USERNAME:-orthanc}"
ORTHANC_PASSWORD="${ORTHANC_PASSWORD:-orthanc}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

COOKIE_FILE="$(mktemp)"
STUDIES_JSON="$(mktemp)"
INSTANCES_JSON="$(mktemp)"
JOB_JSON="$(mktemp)"
AUDIT_JSON="$(mktemp)"

cleanup() {
  rm -f "$COOKIE_FILE" "$STUDIES_JSON" "$INSTANCES_JSON" "$JOB_JSON" "$AUDIT_JSON"
}
trap cleanup EXIT

echo "Checking backend health..."
curl -fsS "$BACKEND_URL/api/health" >/dev/null

echo "Checking AI service health..."
curl -fsS "$AI_SERVICE_URL/health" >/dev/null

echo "Checking Orthanc health..."
curl -fsS -u "$ORTHANC_USERNAME:$ORTHANC_PASSWORD" "$ORTHANC_URL/system" >/dev/null

echo "Logging in to backend..."
curl -fsS -c "$COOKIE_FILE" \
  -H "Content-Type: application/json" \
  -d '{"username":"demo","password":"demo"}' \
  "$BACKEND_URL/api/auth/login" >/dev/null

echo "Listing studies..."
curl -fsS -b "$COOKIE_FILE" "$BACKEND_URL/api/studies" > "$STUDIES_JSON"
STUDY_UID="$($PYTHON_BIN - "$STUDIES_JSON" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
if not data:
    raise SystemExit("No studies found. Run make seed first.")
preferred = None
for study in data:
    if study.get("patientId") in ("ANON002", "ANON001") and "CHEST DEMO" in (study.get("studyDescription") or ""):
        preferred = study
        break
if preferred is None:
    preferred = data[0]
print(preferred["studyInstanceUid"])
PY
)"

echo "Listing instances for $STUDY_UID..."
curl -fsS -b "$COOKIE_FILE" "$BACKEND_URL/api/studies/$STUDY_UID/instances" > "$INSTANCES_JSON"
# 이미 생성된 AI Secondary Capture가 섞여 있을 수 있어, 가능하면 원본 DICOM 인스턴스를 우선 선택한다.
read -r SERIES_UID SOP_UID < <($PYTHON_BIN - "$INSTANCES_JSON" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
if not data:
    raise SystemExit("No instances found for selected study.")
secondary_capture = "1.2.840.10008.5.1.4.1.1.7"
first = next((item for item in data if item.get("sopClassUid") != secondary_capture), data[0])
print(first["seriesInstanceUid"], first["sopInstanceUid"])
PY
)

echo "Running QC..."
curl -fsS -b "$COOKIE_FILE" \
  -H "Content-Type: application/json" \
  -d "{\"studyInstanceUid\":\"$STUDY_UID\",\"seriesInstanceUid\":\"$SERIES_UID\",\"sopInstanceUid\":\"$SOP_UID\"}" \
  "$BACKEND_URL/api/qc/validate" >/dev/null

echo "Creating AI job..."
curl -fsS -b "$COOKIE_FILE" \
  -H "Content-Type: application/json" \
  -d "{\"studyInstanceUid\":\"$STUDY_UID\",\"seriesInstanceUid\":\"$SERIES_UID\",\"sopInstanceUid\":\"$SOP_UID\",\"windowPreset\":\"chest\"}" \
  "$BACKEND_URL/api/ai/jobs" > "$JOB_JSON"
JOB_ID="$($PYTHON_BIN - "$JOB_JSON" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["jobId"])
PY
)"

echo "Polling job $JOB_ID..."
for _ in $(seq 1 45); do
  curl -fsS -b "$COOKIE_FILE" "$BACKEND_URL/api/ai/jobs/$JOB_ID" > "$JOB_JSON"
  STATUS="$($PYTHON_BIN - "$JOB_JSON" <<'PY'
import json, sys
print(json.load(open(sys.argv[1]))["status"])
PY
)"
  echo "status=$STATUS"
  if [[ "$STATUS" == "COMPLETED_VERIFIED" || "$STATUS" == "COMPLETED" ]]; then
    break
  fi
  if [[ "$STATUS" == "FAILED" || "$STATUS" == "BLOCKED_BY_QC" ]]; then
    cat "$JOB_JSON"
    exit 1
  fi
  sleep 2
done

if [[ "$STATUS" != "COMPLETED_VERIFIED" && "$STATUS" != "COMPLETED" ]]; then
  echo "AI job did not complete in time"
  exit 1
fi

echo "Verifying STOW and read-back status..."
$PYTHON_BIN - "$JOB_JSON" <<'PY'
import json, sys
job = json.load(open(sys.argv[1]))
status = job.get("status")
stow = job.get("stowStatus")
readback = job.get("readbackStatus")
if status != "COMPLETED_VERIFIED":
    raise SystemExit(f"Expected COMPLETED_VERIFIED, got {status}")
if stow != "STOW_RS_STORED":
    raise SystemExit(f"Expected STOW_RS_STORED, got {stow}")
if readback != "READBACK_VERIFIED":
    raise SystemExit(f"Expected READBACK_VERIFIED, got {readback}")
# STOW 요청 성공만으로 끝내지 않고, 생성 UID를 다시 조회한 read-back event까지 확인한다.
timeline = [event.get("eventType") for event in job.get("timeline", [])]
for required in ["QC_STARTED", "AI_INFERENCE_COMPLETED", "STOW_UPLOADED", "ORTHANC_READBACK_VERIFIED", "COMPLETED_VERIFIED"]:
    if required not in timeline:
        raise SystemExit(f"Missing timeline event: {required}")
PY

echo "Verifying generated DICOM artifact..."
curl -fsS -b "$COOKIE_FILE" "$BACKEND_URL/api/ai/jobs/$JOB_ID/result-dicom" >/dev/null

echo "Verifying audit log..."
curl -fsS -b "$COOKIE_FILE" "$BACKEND_URL/api/audit" > "$AUDIT_JSON"
$PYTHON_BIN - "$AUDIT_JSON" <<'PY'
import json, sys
audit = json.load(open(sys.argv[1]))
actions = {item.get("action") for item in audit}
for required in ["QC_VALIDATE", "AI_INFERENCE", "DICOM_STOW_RESULT", "ORTHANC_READBACK_VERIFIED"]:
    if required not in actions:
        raise SystemExit(f"Missing audit action: {required}")
PY

echo "Smoke test passed: health, seeded study, QC, AI, STOW-RS, read-back, artifacts, and audit log verified."
