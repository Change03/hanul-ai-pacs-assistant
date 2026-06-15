from __future__ import annotations

import base64
from typing import Annotated

from fastapi import FastAPI, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response

from app.dicom_processing import (
    b64_png,
    build_secondary_capture,
    make_heatmap,
    make_overlay,
    preprocess_dicom,
    render_preview_png,
)
from app.providers import load_provider
from app.schemas import InferResponse

app = FastAPI(title="Hanul AI-PACS Assistant AI Service", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

provider = load_provider()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "UP", "provider": provider.name}


@app.post("/render-preview")
async def render_preview(
    file: Annotated[UploadFile, File()],
    windowPreset: Annotated[str, Form()] = "auto",
) -> Response:
    # 외부 도구나 form 기반 호출을 위한 multipart preview endpoint다.
    data = await file.read()
    return render_preview_bytes(data, windowPreset)


@app.post("/render-preview-raw")
async def render_preview_raw(
    request: Request,
    windowPreset: Annotated[str, Query()] = "auto",
) -> Response:
    # 백엔드 gateway는 원본 DICOM bytes를 그대로 전달하므로 multipart 대신 raw body endpoint를 사용한다.
    data = await request.body()
    return render_preview_bytes(data, windowPreset)


def render_preview_bytes(data: bytes, window_preset: str) -> Response:
    try:
        # preview는 진단용 viewer가 아니라 데모 화면 표시용 PNG 렌더링이다.
        return Response(render_preview_png(data, window_preset), media_type="image/png")
    except Exception as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc


@app.post("/infer", response_model=InferResponse)
async def infer(
    file: Annotated[UploadFile, File()],
    windowPreset: Annotated[str, Form()] = "chest",
) -> dict:
    # multipart infer는 수동 테스트와 확장 가능성을 위해 유지한다.
    data = await file.read()
    return infer_bytes(data, windowPreset)


@app.post("/infer-raw", response_model=InferResponse)
async def infer_raw(
    request: Request,
    windowPreset: Annotated[str, Query()] = "chest",
) -> dict:
    # 운영 데모 경로에서는 백엔드가 DICOM bytes를 raw로 넘겨 인증/감사 흐름을 단순하게 유지한다.
    data = await request.body()
    return infer_bytes(data, windowPreset)


def infer_bytes(data: bytes, window_preset: str) -> dict:
    try:
        # 전처리 결과에는 모델 입력 이미지와 UI에 보여줄 window/rescale metadata가 함께 들어 있다.
        processed = preprocess_dicom(data, window_preset)
        result = provider.infer(processed)
        # heatmap/overlay는 모델 결과를 사람이 빠르게 확인하기 위한 시각 산출물이다.
        heatmap = make_heatmap(processed.image)
        overlay = make_overlay(processed.image, heatmap, result.boxes)
        summary = {
            "demoOnly": True,
            "modelProvider": result.provider,
            "findingLabel": result.label,
            "score": result.score,
            "boxes": result.boxes,
            "disclaimer": "Demo only. Not for clinical use. No real patient data.",
        }
        # 먼저 UID를 확정한 뒤 summary에 넣고, 같은 UID로 최종 Secondary Capture DICOM을 다시 만든다.
        _, result_series_uid, result_sop_uid = build_secondary_capture(processed.dataset, overlay, summary)
        summary["resultSeriesInstanceUID"] = result_series_uid
        summary["resultSopInstanceUID"] = result_sop_uid
        result_dicom, _, _ = build_secondary_capture(
            processed.dataset,
            overlay,
            summary,
            result_series_uid,
            result_sop_uid,
        )
        return {
            "modelProvider": result.provider,
            "findingLabel": result.label,
            "score": result.score,
            "boxes": result.boxes,
            "heatmapPngBase64": b64_png(heatmap),
            "overlayPngBase64": b64_png(overlay),
            "resultDicomBase64": base64.b64encode(result_dicom).decode("ascii"),
            "preprocessing": processed.metadata,
            "warnings": processed.warnings + result.warnings,
        }
    except Exception as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
