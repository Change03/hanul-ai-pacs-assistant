from pydantic import BaseModel, Field


class Box(BaseModel):
    x: int
    y: int
    width: int
    height: int
    label: str
    score: float


class PreprocessingMetadata(BaseModel):
    rows: int
    columns: int
    windowCenter: float
    windowWidth: float
    rescaleSlope: float
    rescaleIntercept: float


class InferResponse(BaseModel):
    modelProvider: str = Field(pattern="^(ONNX|DEMO_FALLBACK|ANTHROPIC)$")
    findingLabel: str
    score: float
    boxes: list[Box]
    heatmapPngBase64: str
    overlayPngBase64: str
    resultDicomBase64: str
    preprocessing: PreprocessingMetadata
    warnings: list[str] = []
