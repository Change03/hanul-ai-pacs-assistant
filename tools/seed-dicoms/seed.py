from __future__ import annotations

import argparse
import io
import json
import os
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pydicom
import requests
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian

UID_ROOT = "1.2.826.0.1.3680043.10.543.20260615"
SOP_CLASS_DX = "1.2.840.10008.5.1.4.1.1.1.1"
DATASET_VERSION = "2026.06.demo.v2"


# upload=True 케이스만 Orthanc에 기본 업로드한다. 실패/PHI/corrupt 케이스는 QC 데모용 로컬 샘플로 남긴다.
CASES: list[dict[str, Any]] = [
    {
        "caseId": "ANON001_normal_like",
        "caseType": "normal_like",
        "studyDescription": "CHEST DEMO NORMAL",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": False,
        "lowContrast": False,
        "patientId": "ANON001",
        "patientName": "ANON^DEMO001",
        "expectedQcStatus": "PASS",
        "expectedAiFinding": "No acute finding demo",
        "expectedRiskBand": "LOW_DEMO",
        "expectedBox": None,
        "upload": True,
        "notes": "Baseline synthetic single-frame grayscale demo DICOM.",
    },
    {
        "caseId": "ANON002_bright_opacity_demo",
        "caseType": "bright_opacity_demo",
        "studyDescription": "CHEST DEMO OPACITY",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": True,
        "lowContrast": False,
        "patientId": "ANON002",
        "patientName": "ANON^DEMO002",
        "expectedQcStatus": "PASS",
        "expectedAiFinding": "Opacity demo",
        "expectedRiskBand": "HIGH_DEMO",
        "expectedBox": {"approximate": True, "label": "Opacity demo"},
        "upload": True,
        "notes": "Contains a synthetic bright region that the deterministic demo provider should flag.",
    },
    {
        "caseId": "ANON003_low_contrast",
        "caseType": "low_contrast",
        "studyDescription": "CHEST DEMO LOW CONTRAST",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": False,
        "lowContrast": True,
        "patientId": "ANON003",
        "patientName": "ANON^DEMO003",
        "expectedQcStatus": "PASS",
        "expectedAiFinding": "Low confidence demo",
        "expectedRiskBand": "LOW_DEMO",
        "expectedBox": None,
        "upload": True,
        "notes": "Valid but intentionally low contrast for low-confidence demo behavior.",
    },
    {
        "caseId": "ANON004_missing_optional_tag",
        "caseType": "missing_optional_tag",
        "studyDescription": "",
        "seriesDescription": "",
        "opacity": False,
        "lowContrast": False,
        "patientId": "ANON004",
        "patientName": "ANON^DEMO004",
        "expectedQcStatus": "WARN",
        "expectedAiFinding": "No acute finding demo",
        "expectedRiskBand": "LOW_DEMO",
        "expectedBox": None,
        "upload": True,
        "notes": "Valid DICOM with optional descriptions omitted to demonstrate QC WARN.",
    },
    {
        "caseId": "ANON005_normal_followup",
        "caseType": "normal_like",
        "studyDescription": "CHEST DEMO FOLLOWUP",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": False,
        "lowContrast": False,
        "patientId": "ANON005",
        "patientName": "ANON^DEMO005",
        "expectedQcStatus": "PASS",
        "expectedAiFinding": "No acute finding demo",
        "expectedRiskBand": "LOW_DEMO",
        "expectedBox": None,
        "upload": True,
        "notes": "Second valid normal-like case for list/filter demos.",
    },
    {
        "caseId": "NEG001_phi_like_negative_case",
        "caseType": "phi_like_negative_case",
        "studyDescription": "CHEST DEMO PHI NEGATIVE",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": False,
        "lowContrast": False,
        "patientId": "REAL123",
        "patientName": "JOHN^SMITH",
        "expectedQcStatus": "FAIL",
        "expectedAiFinding": "BLOCKED_BY_QC",
        "expectedRiskBand": "N/A",
        "expectedBox": None,
        "upload": False,
        "notes": "Local-only QC negative case with PHI-like identifiers.",
    },
    {
        "caseId": "NEG002_missing_pixeldata_negative_case",
        "caseType": "missing_pixeldata_negative_case",
        "studyDescription": "CHEST DEMO MISSING PIXELDATA",
        "seriesDescription": "PA CHEST SYNTHETIC",
        "opacity": False,
        "lowContrast": False,
        "patientId": "ANON901",
        "patientName": "ANON^DEMO901",
        "includePixelData": False,
        "expectedQcStatus": "FAIL",
        "expectedAiFinding": "BLOCKED_BY_QC",
        "expectedRiskBand": "N/A",
        "expectedBox": None,
        "upload": False,
        "notes": "Local-only QC negative case without PixelData.",
    },
    {
        "caseId": "NEG003_corrupted_invalid_file",
        "caseType": "corrupted_invalid_file",
        "expectedQcStatus": "FAIL",
        "expectedAiFinding": "BLOCKED_BY_QC",
        "expectedRiskBand": "N/A",
        "expectedBox": None,
        "upload": False,
        "notes": "Intentionally not a DICOM file.",
    },
]


def chest_like_image(index: int, opacity: bool, low_contrast: bool) -> np.ndarray:
    # 실제 환자 영상이 아니라, 흉부 X-ray처럼 보이는 합성 grayscale 픽셀을 절차적으로 만든다.
    rows, cols = 512, 512
    yy, xx = np.mgrid[0:rows, 0:cols]
    rng = np.random.default_rng(1000 + index)
    base = 1600 + 90 * rng.normal(size=(rows, cols))
    lung_left = ((xx - 190) / 95) ** 2 + ((yy - 260) / 170) ** 2 < 1
    lung_right = ((xx - 322) / 95) ** 2 + ((yy - 260) / 170) ** 2 < 1
    lungs = lung_left | lung_right
    image = base.copy()
    image[lungs] -= 620
    for rib in range(8):
        curve = 95 + rib * 38 + 0.00095 * (xx - 256) ** 2
        image += 105 * np.exp(-((yy - curve) ** 2) / 30)
    image += 280 * np.exp(-((xx - 256) ** 2) / 260)
    image += 180 * np.exp(-((yy - 380) ** 2) / 2400)
    if opacity:
        blob = np.exp(-(((xx - 335) ** 2) / 2100 + ((yy - 245) ** 2) / 1600))
        image += 1450 * blob
    if low_contrast:
        image = 1280 + (image - image.mean()) * 0.22
    return np.clip(image, 0, 4095).astype(np.uint16)


def make_dicom(case_index: int, case: dict[str, Any]) -> bytes:
    # 모든 valid/negative DICOM 케이스는 같은 UID root 아래에서 재현 가능하게 생성한다.
    study_uid = f"{UID_ROOT}.{case_index}"
    series_uid = f"{study_uid}.1"
    sop_uid = f"{series_uid}.1"
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationVersion = b"\x00\x01"
    file_meta.MediaStorageSOPClassUID = SOP_CLASS_DX
    file_meta.MediaStorageSOPInstanceUID = sop_uid
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    file_meta.ImplementationClassUID = f"{UID_ROOT}.999"

    ds = FileDataset(None, {}, file_meta=file_meta, preamble=b"\0" * 128)
    ds.SOPClassUID = SOP_CLASS_DX
    ds.SOPInstanceUID = sop_uid
    ds.StudyInstanceUID = study_uid
    ds.SeriesInstanceUID = series_uid
    ds.Modality = "DX"
    ds.PatientID = case["patientId"]
    ds.PatientName = case["patientName"]
    ds.PatientBirthDate = ""
    ds.PatientSex = "O"
    ds.StudyDate = f"202601{case_index:02d}"
    ds.StudyTime = f"090{case_index}00"
    ds.AccessionNumber = f"DEMO{case_index:03d}"
    ds.Manufacturer = "HANUL_SYNTHETIC"
    ds.InstitutionName = "HANUL_DEMO_LAB"
    ds.BurnedInAnnotation = "NO"
    if case.get("studyDescription"):
        ds.StudyDescription = case["studyDescription"]
    if case.get("seriesDescription"):
        ds.SeriesDescription = case["seriesDescription"]
    ds.BodyPartExamined = "CHEST"
    ds.ViewPosition = "PA"
    ds.ImageComments = "Synthetic demo DICOM. Demo only. Not for clinical use. No real patient data."

    if case.get("includePixelData", True):
        # missing_pixeldata_negative_case는 QC FAIL 시연을 위해 의도적으로 PixelData를 생략한다.
        pixels = chest_like_image(case_index, bool(case.get("opacity")), bool(case.get("lowContrast")))
        ds.Rows, ds.Columns = pixels.shape
        ds.SamplesPerPixel = 1
        ds.PhotometricInterpretation = "MONOCHROME2"
        ds.BitsAllocated = 16
        ds.BitsStored = 12
        ds.HighBit = 11
        ds.PixelRepresentation = 0
        ds.RescaleSlope = "1"
        ds.RescaleIntercept = "0"
        ds.WindowCenter = "1500"
        ds.WindowWidth = "2200"
        ds.InstanceNumber = "1"
        ds.PixelData = pixels.tobytes()

    ds.is_little_endian = True
    ds.is_implicit_VR = False
    out = io.BytesIO()
    ds.save_as(out, write_like_original=False)
    return out.getvalue()


def generate(output: Path) -> list[dict[str, Any]]:
    output.mkdir(parents=True, exist_ok=True)
    generated_cases: list[dict[str, Any]] = []
    dicom_index = 1
    for case in CASES:
        case_out = dict(case)
        if case["caseType"] == "corrupted_invalid_file":
            # 완전히 깨진 파일도 QC 업로드 화면에서 FAIL 시연에 사용한다.
            path = output / f"{case['caseId']}.dcm"
            path.write_bytes(b"This is intentionally not a DICOM file.\n")
            case_out["file"] = str(path.as_posix())
        else:
            data = make_dicom(dicom_index, case)
            study_uid = f"{UID_ROOT}.{dicom_index}"
            series_uid = f"{study_uid}.1"
            sop_uid = f"{series_uid}.1"
            path = output / f"{case['caseId']}.dcm"
            path.write_bytes(data)
            case_out.update(
                {
                    "file": str(path.as_posix()),
                    "studyInstanceUid": study_uid,
                    "seriesInstanceUid": series_uid,
                    "sopInstanceUid": sop_uid,
                }
            )
            dicom_index += 1
        generated_cases.append(case_out)

    manifest = {
        # manifest는 심사자가 케이스 의도와 expected result를 빠르게 확인하기 위한 ground truth 문서다.
        "datasetVersion": DATASET_VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "policy": "Only upload=true cases are uploaded to Orthanc by default. Negative cases stay local for QC demos.",
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


def seed(output: Path, include_negative_upload: bool) -> None:
    cases = generate(output)
    base_url = os.getenv("ORTHANC_BASE_URL", "http://localhost:8042").rstrip("/")
    auth = (os.getenv("ORTHANC_USERNAME", "orthanc"), os.getenv("ORTHANC_PASSWORD", "orthanc"))
    wait_for_orthanc(base_url, auth)
    for case in cases:
        if not case.get("upload") and not include_negative_upload:
            # negative case는 실수로 PACS에 섞이지 않도록 기본 업로드에서 제외한다.
            print(f"kept local-only QC sample {Path(case['file']).name}")
            continue
        stow_file(base_url, auth, Path(case["file"]))
        print(f"uploaded {Path(case['file']).name}")
    print(f"wrote manifest at {output / 'manifest.json'}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate and seed synthetic Hanul AI-PACS DICOM files.")
    parser.add_argument("command", choices=["generate", "seed"])
    parser.add_argument("--output", type=Path, default=Path("output"))
    parser.add_argument("--include-negative-upload", action="store_true", help="Upload local-only negative QC cases to Orthanc. Off by default.")
    args = parser.parse_args()
    if args.command == "generate":
        generate(args.output)
    else:
        seed(args.output, args.include_negative_upload)


if __name__ == "__main__":
    main()
