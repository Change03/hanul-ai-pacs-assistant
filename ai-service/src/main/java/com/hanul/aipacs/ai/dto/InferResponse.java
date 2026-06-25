package com.hanul.aipacs.ai.dto;

import java.util.List;
import java.util.Map;

public record InferResponse(
    String modelProvider,
    String findingLabel,
    double score,
    List<BoundingBoxDto> boxes,
    String heatmapPngBase64,
    String overlayPngBase64,
    String resultDicomBase64,
    Map<String, Object> preprocessing,
    List<String> warnings
) {
}
