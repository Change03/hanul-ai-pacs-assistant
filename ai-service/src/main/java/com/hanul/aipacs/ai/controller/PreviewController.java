package com.hanul.aipacs.ai.controller;

import com.hanul.aipacs.ai.service.PreviewRenderService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class PreviewController {
    private final PreviewRenderService previewRenderService;

    public PreviewController(PreviewRenderService previewRenderService) {
        this.previewRenderService = previewRenderService;
    }

    @PostMapping(path = "/render-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> renderPreview(
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "auto") String windowPreset
    ) throws Exception {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(previewRenderService.renderPreview(file.getBytes(), windowPreset));
    }

    @PostMapping(path = "/render-preview-raw", consumes = "application/dicom")
    public ResponseEntity<byte[]> renderPreviewRaw(
        @RequestBody byte[] dicomBytes,
        @RequestParam(defaultValue = "auto") String windowPreset
    ) {
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(previewRenderService.renderPreview(dicomBytes, windowPreset));
    }
}
