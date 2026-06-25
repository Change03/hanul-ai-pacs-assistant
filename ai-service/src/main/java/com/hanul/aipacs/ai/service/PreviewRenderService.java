package com.hanul.aipacs.ai.service;

import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.util.ImageUtils;
import org.springframework.stereotype.Service;

@Service
public class PreviewRenderService {
    private final DicomPreprocessor dicomPreprocessor;

    public PreviewRenderService(DicomPreprocessor dicomPreprocessor) {
        this.dicomPreprocessor = dicomPreprocessor;
    }

    public byte[] renderPreview(byte[] dicomBytes, String windowPreset) {
        ProcessedDicom processed = dicomPreprocessor.preprocess(dicomBytes, windowPreset);
        return ImageUtils.toPng(processed.image(), processed.columns(), processed.rows());
    }
}
