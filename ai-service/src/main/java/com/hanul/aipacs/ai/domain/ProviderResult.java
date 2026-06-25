package com.hanul.aipacs.ai.domain;

import java.util.List;

public record ProviderResult(
    String provider,
    String label,
    double score,
    List<BoundingBox> boxes,
    List<String> warnings
) {
}
