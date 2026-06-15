from __future__ import annotations

import argparse
import json
import os
import shutil
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pydicom
import requests

DATASET_VERSION = "2026.06.user-provided.v1"
DEFAULT_SAMPLE_DIR = Path(__file__).resolve().parent / "samples" / "user-provided"


def text_value(dataset: Any, name: str) -> str:
    value = getattr(dataset, name, "")
    return "" if value is None else str(value)


def number_value(dataset: Any, name: str) -> int | None:
    value = getattr(dataset, name, None)
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def case_type(path: Path, dataset: Any) -> str:
    modality = text_value(dataset, "Modality").upper()
    body_part = text_value(dataset, "BodyPartExamined").upper()
    if modality == "CR" and body_part == "BREAST":
        return "breast_cr"
    if modality == "CT" and body_part == "HEAD":
        return "brain_ct"
    return "user_provided_dicom"


def expected_ai_finding(dataset: Any) -> str:
    modality = text_value(dataset, "Modality").upper()
    if modality == "CR":
        return "Breast CR demo image"
    if modality == "CT":
        return "Brain CT demo image"
    return "User-provided demo image"


def sample_files(sample_dir: Path) -> list[Path]:
    files = sorted(sample_dir.glob("*.dcm"))
    if not files:
        raise RuntimeError(f"No DICOM samples found in {sample_dir}")
    return files


def build_case(source_path: Path, output_path: Path) -> dict[str, Any]:
    dataset = pydicom.dcmread(source_path, stop_before_pixels=True, force=True)
    file_meta = getattr(dataset, "file_meta", None)
    transfer_syntax = "" if file_meta is None else text_value(file_meta, "TransferSyntaxUID")
    return {
        "caseId": source_path.stem,
        "caseType": case_type(source_path, dataset),
        "file": output_path.as_posix(),
        "sourceFile": source_path.as_posix(),
        "patientId": text_value(dataset, "PatientID"),
        "patientName": text_value(dataset, "PatientName"),
        "studyDescription": text_value(dataset, "StudyDescription"),
        "seriesDescription": text_value(dataset, "SeriesDescription"),
        "modality": text_value(dataset, "Modality"),
        "bodyPartExamined": text_value(dataset, "BodyPartExamined"),
        "studyDate": text_value(dataset, "StudyDate"),
        "transferSyntaxUid": transfer_syntax,
        "studyInstanceUid": text_value(dataset, "StudyInstanceUID"),
        "seriesInstanceUid": text_value(dataset, "SeriesInstanceUID"),
        "sopInstanceUid": text_value(dataset, "SOPInstanceUID"),
        "rows": number_value(dataset, "Rows"),
        "columns": number_value(dataset, "Columns"),
        "expectedQcStatus": "PASS",
        "expectedAiFinding": expected_ai_finding(dataset),
        "expectedRiskBand": "DEMO_ONLY",
        "upload": True,
        "notes": "Anonymized user-provided DICOM bundled with the repository.",
    }


def generate(output: Path, sample_dir: Path = DEFAULT_SAMPLE_DIR) -> list[dict[str, Any]]:
    output.mkdir(parents=True, exist_ok=True)
    for old_file in output.glob("*.dcm"):
        old_file.unlink()

    generated_cases: list[dict[str, Any]] = []
    for source_path in sample_files(sample_dir):
        output_path = output / source_path.name
        shutil.copy2(source_path, output_path)
        generated_cases.append(build_case(source_path, output_path))

    manifest = {
        "datasetVersion": DATASET_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "policy": "Only bundled anonymized user-provided DICOM files are uploaded. Synthetic image samples are no longer generated.",
        "sampleDirectory": sample_dir.as_posix(),
        "cases": generated_cases,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")
    return generated_cases


def wait_for_orthanc(base_url: str, auth: tuple[str, str]) -> None:
    for _ in range(90):
        try:
            response = requests.get(f"{base_url}/system", auth=auth, timeout=3)
            if response.ok:
                return
        except requests.RequestException:
            pass
        time.sleep(2)
    raise RuntimeError(f"Orthanc did not become ready at {base_url}")


def replace_existing_studies(base_url: str, auth: tuple[str, str]) -> None:
    response = requests.get(f"{base_url}/studies", auth=auth, timeout=10)
    response.raise_for_status()
    for study_id in response.json():
        delete_response = requests.delete(f"{base_url}/studies/{study_id}", auth=auth, timeout=15)
        if delete_response.status_code not in (200, 202, 204, 404):
            raise RuntimeError(f"Failed to delete Orthanc study {study_id}: {delete_response.status_code} {delete_response.text[:300]}")
        print(f"deleted existing Orthanc study {study_id}")


def stow_file(base_url: str, auth: tuple[str, str], path: Path) -> None:
    boundary = f"hanul-seed-{int(time.time() * 1000)}"
    body = (
        f"--{boundary}\r\n"
        "Content-Type: application/dicom\r\n\r\n"
    ).encode("ascii") + path.read_bytes() + f"\r\n--{boundary}--\r\n".encode("ascii")
    headers = {"Content-Type": f'multipart/related; type="application/dicom"; boundary={boundary}'}
    response = requests.post(f"{base_url}/dicom-web/studies", data=body, headers=headers, auth=auth, timeout=30)
    if response.status_code not in (200, 202, 204):
        raise RuntimeError(f"STOW-RS upload failed for {path.name}: {response.status_code} {response.text[:300]}")


def seed(output: Path, sample_dir: Path, replace_existing: bool) -> None:
    cases = generate(output, sample_dir)
    base_url = os.getenv("ORTHANC_BASE_URL", "http://localhost:8042").rstrip("/")
    auth = (os.getenv("ORTHANC_USERNAME", "orthanc"), os.getenv("ORTHANC_PASSWORD", "orthanc"))
    wait_for_orthanc(base_url, auth)
    if replace_existing:
        replace_existing_studies(base_url, auth)
    for case in cases:
        stow_file(base_url, auth, Path(case["file"]))
        print(f"uploaded {Path(case['file']).name}")
    print(f"wrote manifest at {output / 'manifest.json'}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Copy and seed bundled anonymized Hanul AI-PACS DICOM files.")
    parser.add_argument("command", choices=["generate", "seed"])
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--sample-dir", type=Path, default=DEFAULT_SAMPLE_DIR)
    parser.add_argument("--replace-existing", action="store_true", help="Delete existing Orthanc studies before uploading bundled samples.")
    args = parser.parse_args()
    if args.command == "generate":
        generate(args.output, args.sample_dir)
    else:
        seed(args.output, args.sample_dir, args.replace_existing)


if __name__ == "__main__":
    main()
