package com.hanul.aipacs.ai.dto;

public record BoundingBoxDto(
    int x,
    int y,
    int width,
    int height,
    String label,
    double score
) {
}
