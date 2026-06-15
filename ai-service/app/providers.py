from __future__ import annotations

import base64
import json
import os
from dataclasses import dataclass
from typing import Any

import httpx
import numpy as np

from app.dicom_processing import PreprocessedDicom, png_bytes


@dataclass
class ProviderResult:
    provider: str
    label: str
    score: float
    boxes: list[dict[str, Any]]
    warnings: list[str]


ALLOWED_LABELS = {
    "Opacity demo",
    "Low confidence demo",
    "No acute finding demo",
}


def _extract_json(text: str) -> dict[str, Any]:
    try:
        parsed = json.loads(text)
    except json.JSONDecodeError:
        start = text.find("{")
        end = text.rfind("}")
        if start < 0 or end <= start:
            raise ValueError("Anthropic response did not contain JSON") from None
        parsed = json.loads(text[start : end + 1])
    if not isinstance(parsed, dict):
        raise ValueError("Anthropic response JSON must be an object")
    return parsed


def _bounded_score(value: Any, default: float = 0.5) -> float:
    try:
        score = float(value)
    except (TypeError, ValueError):
        score = default
    return round(max(0.0, min(1.0, score)), 4)


class DemoProvider:
    name = "DEMO_FALLBACK"

    def infer(self, processed: PreprocessedDicom) -> ProviderResult:
        # 임상 모델이 아니라 밝기/대비 분포를 이용한 결정론적 데모 알고리즘이다.
        image = processed.image
        h, w = image.shape
        p05, p50, p95, p99 = np.percentile(image, [5, 50, 95, 99])
        bright_mask = image >= max(p95, 170)
        bright_ratio = float(bright_mask.mean())
        contrast = float((p95 - p05) / 255.0)

        if bright_ratio > 0.025 and p99 > 210:
            # 합성 opacity case는 밝은 국소 영역을 넣어두었으므로 그 영역을 데모 소견으로 표시한다.
            label = "Opacity demo"
            score = min(0.96, 0.58 + bright_ratio * 7.0 + contrast * 0.25)
            coords = np.argwhere(bright_mask)
        elif contrast < 0.18:
            # 대비가 낮은 synthetic case는 낮은 신뢰도 흐름을 보여주기 위한 분기다.
            label = "Low confidence demo"
            score = 0.36
            coords = np.argwhere(image >= p95)
        else:
            label = "No acute finding demo"
            score = max(0.08, min(0.31, 0.2 + (p50 - 90.0) / 900.0))
            coords = np.argwhere(image >= p99)

        boxes: list[dict[str, Any]] = []
        if len(coords) > 4 and label != "No acute finding demo":
            y0, x0 = coords.min(axis=0)
            y1, x1 = coords.max(axis=0)
            pad_x = max(6, int(w * 0.025))
            pad_y = max(6, int(h * 0.025))
            x0 = int(max(0, x0 - pad_x))
            y0 = int(max(0, y0 - pad_y))
            x1 = int(min(w - 1, x1 + pad_x))
            y1 = int(min(h - 1, y1 + pad_y))
            boxes.append(
                {
                    "x": x0,
                    "y": y0,
                    "width": max(1, x1 - x0),
                    "height": max(1, y1 - y0),
                    "label": label,
                    "score": round(float(score), 4),
                }
            )

        return ProviderResult(
            provider=self.name,
            label=label,
            score=round(float(score), 4),
            boxes=boxes,
            warnings=["No ONNX model was found; deterministic demo fallback was used."],
        )


class OnnxProvider:
    name = "ONNX"

    def __init__(self, model_path: str):
        import onnxruntime as ort

        self.session = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
        self.input_name = self.session.get_inputs()[0].name

    def infer(self, processed: PreprocessedDicom) -> ProviderResult:
        # 실제 모델 파일이 있는 경우를 위한 얇은 ONNX adapter다. 기본 배포는 DEMO_FALLBACK을 사용한다.
        tensor = processed.resized.astype(np.float32) / 255.0
        tensor = tensor[None, None, :, :]
        outputs = self.session.run(None, {self.input_name: tensor})
        raw = np.asarray(outputs[0]).reshape(-1)
        score = float(1.0 / (1.0 + np.exp(-raw[0]))) if raw.size else 0.0
        label = "Opacity demo" if score >= 0.5 else "No acute finding demo"
        return ProviderResult(
            provider=self.name,
            label=label,
            score=round(score, 4),
            boxes=[],
            warnings=[],
        )


class AnthropicProvider:
    name = "ANTHROPIC"

    def __init__(
        self,
        api_key: str,
        model: str = "claude-sonnet-4-5",
        base_url: str = "https://api.anthropic.com",
        timeout_seconds: float = 45.0,
    ):
        if not api_key:
            raise RuntimeError("ANTHROPIC_API_KEY is required when AI_PROVIDER=ANTHROPIC")
        self.api_key = api_key
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.timeout_seconds = timeout_seconds

    def infer(self, processed: PreprocessedDicom) -> ProviderResult:
        # 실험 옵션이다. synthetic PNG만 보내며, 반환 라벨도 데모용 허용 라벨로 제한한다.
        prompt = {
            "task": "Classify this demo medical image for a PACS portfolio application.",
            "allowedLabels": sorted(ALLOWED_LABELS),
            "rules": [
                "Return JSON only.",
                "This is not for clinical diagnosis.",
                "Do not claim certainty or provide treatment advice.",
                "Use Low confidence demo if the image is outside scope or uncertain.",
                "Leave boxes empty unless you are certain about pixel coordinates.",
            ],
            "metadata": {
                "modality": str(getattr(processed.dataset, "Modality", "")),
                "rows": processed.metadata["rows"],
                "columns": processed.metadata["columns"],
                "windowCenter": processed.metadata["windowCenter"],
                "windowWidth": processed.metadata["windowWidth"],
            },
            "responseSchema": {
                "label": "one allowedLabels value",
                "score": "number from 0 to 1",
                "rationale": "brief non-clinical explanation",
                "boxes": [],
            },
        }
        payload = {
            "model": self.model,
            "max_tokens": 400,
            "temperature": 0,
            "system": (
                "You are a cautious demo-only medical imaging assistant. "
                "You must not provide clinical diagnosis, triage, treatment, or patient-specific advice."
            ),
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image",
                            "source": {
                                "type": "base64",
                                "media_type": "image/png",
                                "data": base64.b64encode(png_bytes(processed.image)).decode("ascii"),
                            },
                        },
                        {
                            "type": "text",
                            "text": json.dumps(prompt, separators=(",", ":")),
                        },
                    ],
                }
            ],
        }
        headers = {
            "x-api-key": self.api_key,
            "anthropic-version": "2023-06-01",
            "content-type": "application/json",
        }
        response = httpx.post(
            f"{self.base_url}/v1/messages",
            headers=headers,
            json=payload,
            timeout=self.timeout_seconds,
        )
        response.raise_for_status()
        data = response.json()
        text_parts = [
            block.get("text", "")
            for block in data.get("content", [])
            if isinstance(block, dict) and block.get("type") == "text"
        ]
        parsed = _extract_json("\n".join(text_parts))
        label = str(parsed.get("label") or parsed.get("findingLabel") or "Low confidence demo")
        if label not in ALLOWED_LABELS:
            label = "Low confidence demo"
        boxes = parsed.get("boxes", [])
        if not isinstance(boxes, list):
            boxes = []
        rationale = str(parsed.get("rationale") or "").strip()
        warnings = [
            "Anthropic Claude analyzed an anonymized PNG rendering of the DICOM pixels. Demo only. Not for clinical use. No real patient data.",
        ]
        if rationale:
            warnings.append(f"Claude rationale: {rationale[:240]}")
        return ProviderResult(
            provider=self.name,
            label=label,
            score=_bounded_score(parsed.get("score")),
            boxes=boxes,
            warnings=warnings,
        )


def load_provider() -> DemoProvider | OnnxProvider | AnthropicProvider:
    # 기본값은 외부 API나 모델 파일이 없어도 동작하는 안전한 DEMO_FALLBACK이다.
    provider_name = os.getenv("AI_PROVIDER", "").strip().upper()
    if provider_name == "ANTHROPIC":
        return AnthropicProvider(
            api_key=os.getenv("ANTHROPIC_API_KEY", ""),
            model=os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-5"),
            base_url=os.getenv("ANTHROPIC_BASE_URL", "https://api.anthropic.com"),
        )

    model_path = os.getenv("MODEL_PATH", "./models/chest_demo.onnx")
    if provider_name == "DEMO_FALLBACK":
        return DemoProvider()
    if provider_name == "ONNX":
        if not os.path.exists(model_path):
            raise RuntimeError(f"MODEL_PATH does not exist: {model_path}")
        return OnnxProvider(model_path)
    if provider_name and provider_name != "AUTO":
        raise RuntimeError("AI_PROVIDER must be DEMO_FALLBACK, ONNX, ANTHROPIC, or AUTO")
    if os.path.exists(model_path):
        return OnnxProvider(model_path)
    return DemoProvider()
