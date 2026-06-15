import io

import numpy as np
import pydicom
from PIL import Image
from pydicom.dataset import FileDataset, FileMetaDataset
from pydicom.uid import ExplicitVRLittleEndian, SecondaryCaptureImageStorage, generate_uid

from app.dicom_processing import build_secondary_capture, preprocess_dicom, render_preview_png
from app.providers import AnthropicProvider, DemoProvider, load_provider


def sample_dicom(opacity: bool = False, low_contrast: bool = False) -> bytes:
    file_meta = FileMetaDataset()
    sop_uid = generate_uid()
    file_meta.FileMetaInformationVersion = b"\x00\x01"
    file_meta.MediaStorageSOPClassUID = SecondaryCaptureImageStorage
    file_meta.MediaStorageSOPInstanceUID = sop_uid
    file_meta.TransferSyntaxUID = ExplicitVRLittleEndian
    file_meta.ImplementationClassUID = generate_uid()

    ds = FileDataset(None, {}, file_meta=file_meta, preamble=b"\0" * 128)
    ds.SOPClassUID = SecondaryCaptureImageStorage
    ds.SOPInstanceUID = sop_uid
    ds.StudyInstanceUID = generate_uid()
    ds.SeriesInstanceUID = generate_uid()
    ds.Modality = "DX"
    ds.PatientID = "ANON001"
    ds.PatientName = "ANON^DEMO001"
    ds.Rows = 128
    ds.Columns = 128
    ds.SamplesPerPixel = 1
    ds.PhotometricInterpretation = "MONOCHROME2"
    ds.BitsAllocated = 16
    ds.BitsStored = 12
    ds.HighBit = 11
    ds.PixelRepresentation = 0
    base = np.full((128, 128), 1024 if low_contrast else 500, dtype=np.uint16)
    if not low_contrast:
        yy, xx = np.mgrid[0:128, 0:128]
        base += (220 * np.exp(-(((xx - 64) ** 2 + (yy - 70) ** 2) / 1800))).astype(np.uint16)
    if opacity:
        base[42:72, 70:102] = 3900
    ds.PixelData = base.tobytes()
    ds.is_little_endian = True
    ds.is_implicit_VR = False
    out = io.BytesIO()
    ds.save_as(out, write_like_original=False)
    return out.getvalue()


def test_preprocessing_resizes_and_records_metadata():
    processed = preprocess_dicom(sample_dicom(), "auto")
    assert processed.resized.shape == (224, 224)
    assert processed.metadata["rows"] == 128
    assert processed.metadata["columns"] == 128


def test_demo_provider_detects_bright_opacity():
    processed = preprocess_dicom(sample_dicom(opacity=True), "auto")
    result = DemoProvider().infer(processed)
    assert result.provider == "DEMO_FALLBACK"
    assert result.label == "Opacity demo"
    assert result.boxes


def test_load_provider_selects_anthropic_without_api_call(monkeypatch):
    monkeypatch.setenv("AI_PROVIDER", "ANTHROPIC")
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    monkeypatch.setenv("ANTHROPIC_MODEL", "test-model")
    provider = load_provider()
    assert isinstance(provider, AnthropicProvider)
    assert provider.name == "ANTHROPIC"
    assert provider.model == "test-model"


def test_secondary_capture_result_is_valid_dicom():
    processed = preprocess_dicom(sample_dicom(opacity=True), "auto")
    overlay = np.stack([processed.image, processed.image, processed.image], axis=-1)
    result_bytes, series_uid, sop_uid = build_secondary_capture(
        processed.dataset,
        overlay,
        {"findingLabel": "Opacity demo", "score": 0.8},
    )
    ds = pydicom.dcmread(io.BytesIO(result_bytes))
    assert ds.Modality == "OT"
    assert ds.SeriesDescription == "AI_RESULT_DEMO"
    assert ds.SeriesInstanceUID == series_uid
    assert ds.SOPInstanceUID == sop_uid


def test_preview_rendering_returns_png():
    png = render_preview_png(sample_dicom(), "chest")
    image = Image.open(io.BytesIO(png))
    assert image.format == "PNG"
    assert image.size == (128, 128)
