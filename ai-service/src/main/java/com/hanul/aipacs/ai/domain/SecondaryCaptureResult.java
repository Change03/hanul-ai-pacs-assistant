package com.hanul.aipacs.ai.domain;

public record SecondaryCaptureResult(
    byte[] dicomBytes,
    String seriesInstanceUid,
    String sopInstanceUid
) {
}
