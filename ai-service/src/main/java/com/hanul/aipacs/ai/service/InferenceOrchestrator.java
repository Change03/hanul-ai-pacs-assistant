package com.hanul.aipacs.ai.service;

import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.domain.ProviderResult;
import com.hanul.aipacs.ai.domain.SecondaryCaptureResult;
import com.hanul.aipacs.ai.dto.BoundingBoxDto;
import com.hanul.aipacs.ai.dto.InferResponse;
import com.hanul.aipacs.ai.provider.ProviderResolver;
import com.hanul.aipacs.ai.util.ImageUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InferenceOrchestrator {
    private final DicomPreprocessor dicomPreprocessor;
    private final ProviderResolver providerResolver;
    private final HeatmapService heatmapService;
    private final OverlayRenderService overlayRenderService;
    private final SecondaryCaptureDicomService secondaryCaptureDicomService;

    public InferenceOrchestrator(
        DicomPreprocessor dicomPreprocessor,
        ProviderResolver providerResolver,
        HeatmapService heatmapService,
        OverlayRenderService overlayRenderService,
        SecondaryCaptureDicomService secondaryCaptureDicomService
    ) {
        this.dicomPreprocessor = dicomPreprocessor;
        this.providerResolver = providerResolver;
        this.heatmapService = heatmapService;
        this.overlayRenderService = overlayRenderService;
        this.secondaryCaptureDicomService = secondaryCaptureDicomService;
    }

    public InferResponse infer(byte[] dicomBytes, String windowPreset) {
        ProcessedDicom processed = dicomPreprocessor.preprocess(dicomBytes, windowPreset);
        ProviderResult result = providerResolver.current().infer(processed);
        byte[] heatmap = heatmapService.makeHeatmap(processed.image(), processed.columns(), processed.rows());
        byte[] overlay = overlayRenderService.makeOverlay(processed.image(), heatmap, processed.columns(), processed.rows(), result.boxes());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("demoOnly", true);
        summary.put("modelProvider", result.provider());
        summary.put("findingLabel", result.label());
        summary.put("score", result.score());
        summary.put("boxes", result.boxes());
        summary.put("disclaimer", "Demo only. Not for clinical use. No real patient data.");

        SecondaryCaptureResult provisional = secondaryCaptureDicomService.build(processed.dataset(), overlay, processed.columns(), processed.rows(), summary);
        summary.put("resultSeriesInstanceUID", provisional.seriesInstanceUid());
        summary.put("resultSopInstanceUID", provisional.sopInstanceUid());
        SecondaryCaptureResult finalResult = secondaryCaptureDicomService.build(
            processed.dataset(),
            overlay,
            processed.columns(),
            processed.rows(),
            summary,
            provisional.seriesInstanceUid(),
            provisional.sopInstanceUid()
        );

        return new InferResponse(
            result.provider(),
            result.label(),
            result.score(),
            result.boxes().stream().map(box -> new BoundingBoxDto(box.x(), box.y(), box.width(), box.height(), box.label(), box.score())).toList(),
            ImageUtils.toBase64Png(heatmap, processed.columns(), processed.rows(), true),
            ImageUtils.toBase64Png(overlay, processed.columns(), processed.rows(), true),
            java.util.Base64.getEncoder().encodeToString(finalResult.dicomBytes()),
            processed.metadata(),
            concatWarnings(processed.warnings(), result.warnings())
        );
    }

    private List<String> concatWarnings(List<String> preprocessingWarnings, List<String> providerWarnings) {
        java.util.ArrayList<String> warnings = new java.util.ArrayList<>(preprocessingWarnings);
        warnings.addAll(providerWarnings);
        return warnings;
    }
}
