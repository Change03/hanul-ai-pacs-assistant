package com.hanul.aipacs.ai.domain;

import java.util.List;
import java.util.Map;
import org.dcm4che3.data.Attributes;

public record ProcessedDicom(
    Attributes dataset,
    byte[] image,
    byte[] resized,
    int rows,
    int columns,
    Map<String, Object> metadata,
    List<String> warnings
) {
}
