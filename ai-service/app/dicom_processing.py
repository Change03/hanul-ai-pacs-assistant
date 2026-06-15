from __future__ import annotations

import base64
import io
import json
from dataclasses import dataclass
from typing import Any

import numpy as np
import pydicom
from PIL import Image, ImageDraw
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, SecondaryCaptureImageStorage, generate_uid


WINDOWS: dict[str, tuple[float, float]] = {
    # 데모 preview/inference에서 사용하는 기본 WL/WW preset이다.
    "chest": (40.0, 400.0),
    "lung": (-600.0, 1500.0),
    "bone": (300.0, 1500.0),
}


@dataclass
class PreprocessedDicom:
    dataset: FileDataset
    image: np.ndarray
    resized: np.ndarray
    metadata: dict[str, Any]
    warnings: list[str]


def read_dataset(dicom_bytes: bytes) -> FileDataset:
    return pydicom.dcmread(io.BytesIO(dicom_bytes), force=True)


def _scalar(value: Any, default: float) -> float:
    if value is None:
        return default
    if isinstance(value, (list, tuple)):
        value = value[0] if value else default
    try:
        if hasattr(value, "__iter__") and not isinstance(value, (str, bytes)):
            value = list(value)[0]
        return float(value)
    except Exception:
        return default


def _window(ds: FileDataset, pixels: np.ndarray, preset: str) -> tuple[float, float]:
    # preset이 없으면 DICOM 태그의 WindowCenter/WindowWidth를 우선 사용하고, 없을 때 percentile 기반 auto window를 쓴다.
    preset = (preset or "auto").lower()
    if preset in WINDOWS:
        return WINDOWS[preset]
    center = _scalar(getattr(ds, "WindowCenter", None), float(np.percentile(pixels, 50)))
    width = _scalar(getattr(ds, "WindowWidth", None), float(np.percentile(pixels, 99) - np.percentile(pixels, 1)))
    if width <= 1:
        low, high = np.percentile(pixels, [1, 99])
        center = float((low + high) / 2.0)
        width = float(max(high - low, 1.0))
    return center, width


def normalize_image(pixels: np.ndarray, center: float, width: float) -> np.ndarray:
    low = center - width / 2.0
    high = center + width / 2.0
    clipped = np.clip(pixels, low, high)
    norm = (clipped - low) / max(high - low, 1e-6)
    return (norm * 255.0).astype(np.uint8)


def preprocess_dicom(dicom_bytes: bytes, window_preset: str = "auto") -> PreprocessedDicom:
    # AI provider가 공통으로 사용할 수 있도록 DICOM pixel을 display image와 224x224 tensor 입력으로 정규화한다.
    warnings: list[str] = []
    ds = read_dataset(dicom_bytes)
    if "PixelData" not in ds:
        raise ValueError("DICOM has no PixelData")
    try:
        pixels = ds.pixel_array.astype(np.float32)
    except Exception as exc:
        raise ValueError(f"Unsupported or unreadable pixel data: {exc}") from exc

    slope = _scalar(getattr(ds, "RescaleSlope", 1), 1.0)
    intercept = _scalar(getattr(ds, "RescaleIntercept", 0), 0.0)
    pixels = pixels * slope + intercept

    if getattr(ds, "PhotometricInterpretation", "") == "MONOCHROME1":
        # MONOCHROME1은 밝기 의미가 반대라 preview와 heatmap 해석을 위해 display용으로 뒤집는다.
        pixels = pixels.max() - pixels
        warnings.append("MONOCHROME1 image inverted for display")

    center, width = _window(ds, pixels, window_preset)
    image = normalize_image(pixels, center, width)
    resized = np.array(Image.fromarray(image).resize((224, 224), Image.Resampling.BILINEAR))
    metadata = {
        "rows": int(getattr(ds, "Rows", image.shape[0])),
        "columns": int(getattr(ds, "Columns", image.shape[1])),
        "windowCenter": float(center),
        "windowWidth": float(width),
        "rescaleSlope": float(slope),
        "rescaleIntercept": float(intercept),
    }
    return PreprocessedDicom(ds, image, resized, metadata, warnings)


def png_bytes(image: np.ndarray) -> bytes:
    out = io.BytesIO()
    Image.fromarray(image).save(out, format="PNG")
    return out.getvalue()


def b64_png(image: np.ndarray) -> str:
    return base64.b64encode(png_bytes(image)).decode("ascii")


def make_heatmap(image: np.ndarray, center: tuple[int, int] | None = None) -> np.ndarray:
    # 실제 설명가능성 모델이 아니라, 데모 결과를 시각화하기 위한 synthetic heatmap이다.
    h, w = image.shape
    yy, xx = np.mgrid[0:h, 0:w]
    if center is None:
        threshold = np.percentile(image, 93)
        coords = np.argwhere(image >= threshold)
        if len(coords) == 0:
            center = (w // 2, h // 2)
        else:
            y, x = coords.mean(axis=0)
            center = (int(x), int(y))
    cx, cy = center
    sigma = max(min(h, w) / 5.0, 1.0)
    blob = np.exp(-(((xx - cx) ** 2 + (yy - cy) ** 2) / (2 * sigma**2)))
    blob = (blob * 255).astype(np.uint8)
    heat = np.zeros((h, w, 3), dtype=np.uint8)
    heat[..., 0] = blob
    heat[..., 1] = np.clip(blob * 0.45, 0, 255).astype(np.uint8)
    heat[..., 2] = np.clip(255 - blob, 0, 255).astype(np.uint8)
    return heat


def make_overlay(image: np.ndarray, heatmap: np.ndarray, boxes: list[dict[str, Any]]) -> np.ndarray:
    # 원본 grayscale 위에 heatmap과 bounding box를 합성해 Secondary Capture DICOM의 PixelData로 사용한다.
    gray = np.stack([image, image, image], axis=-1)
    overlay = np.clip(gray * 0.66 + heatmap * 0.34, 0, 255).astype(np.uint8)
    pil = Image.fromarray(overlay)
    draw = ImageDraw.Draw(pil)
    for box in boxes:
        x, y, width, height = int(box["x"]), int(box["y"]), int(box["width"]), int(box["height"])
        draw.rectangle([x, y, x + width, y + height], outline=(82, 255, 214), width=3)
        draw.text((x + 4, max(2, y - 16)), box.get("label", "demo"), fill=(82, 255, 214))
    return np.array(pil)


def render_preview_png(dicom_bytes: bytes, window_preset: str = "auto") -> bytes:
    processed = preprocess_dicom(dicom_bytes, window_preset)
    return png_bytes(processed.image)


def build_secondary_capture(
    source: FileDataset,
    overlay_rgb: np.ndarray,
    summary: dict[str, Any],
    series_uid: str | None = None,
    sop_uid: str | None = None,
) -> tuple[bytes, str, str]:
    # 원본 StudyInstanceUID는 유지하고, 결과용 Series/SOP UID는 새로 발급해 원본 불변성을 지킨다.
    series_uid = series_uid or generate_uid()
    sop_uid = sop_uid or generate_uid()
    file_meta = FileMetaDataset()
    file_meta.FileMetaInformationVersion = b"\x00\x01"
    file_meta.MediaStorageSOPClassUID = SecondaryCaptureImageStorage
    file_meta.MediaStorageSOPInstanceUID = sop_uid
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    file_meta.ImplementationClassUID = generate_uid()

    ds = FileDataset(None, {}, file_meta=file_meta, preamble=b"\0" * 128)
    ds.SOPClassUID = SecondaryCaptureImageStorage
    ds.SOPInstanceUID = sop_uid
    ds.StudyInstanceUID = getattr(source, "StudyInstanceUID", generate_uid())
    ds.SeriesInstanceUID = series_uid
    ds.Modality = "OT"
    ds.SeriesDescription = "AI_RESULT_DEMO"
    ds.ImageType = ["DERIVED", "SECONDARY", "DEMO"]
    ds.ConversionType = "WSD"
    ds.PatientID = getattr(source, "PatientID", "ANON_UNKNOWN")
    ds.PatientName = getattr(source, "PatientName", "ANON^UNKNOWN")
    ds.StudyDate = getattr(source, "StudyDate", "")
    ds.StudyTime = getattr(source, "StudyTime", "")
    ds.ImageComments = json.dumps(summary, separators=(",", ":"))[:1024]
    ds.Rows = int(overlay_rgb.shape[0])
    ds.Columns = int(overlay_rgb.shape[1])
    ds.SamplesPerPixel = 3
    ds.PhotometricInterpretation = "RGB"
    ds.PlanarConfiguration = 0
    ds.BitsAllocated = 8
    ds.BitsStored = 8
    ds.HighBit = 7
    ds.PixelRepresentation = 0
    ds.PixelData = overlay_rgb.astype(np.uint8).tobytes()
    ds.is_little_endian = True
    ds.is_implicit_VR = False

    out = io.BytesIO()
    ds.save_as(out, write_like_original=False)
    return out.getvalue(), series_uid, sop_uid
