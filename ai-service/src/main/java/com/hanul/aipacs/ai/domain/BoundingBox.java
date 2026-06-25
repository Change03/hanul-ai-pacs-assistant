package com.hanul.aipacs.ai.domain;

public record BoundingBox(
    int x,
    int y,
    int width,
    int height,
    String label,
    double score
) {
}
