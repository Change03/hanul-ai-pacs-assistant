package com.hanul.aipacs.ai.controller;

import com.hanul.aipacs.ai.dto.InferResponse;
import com.hanul.aipacs.ai.service.InferenceOrchestrator;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
public class InferenceController {
    private final InferenceOrchestrator orchestrator;

    public InferenceController(InferenceOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping(path = "/infer", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public InferResponse infer(
        @RequestPart("file") MultipartFile file,
        @RequestParam(defaultValue = "chest") String windowPreset
    ) throws Exception {
        return orchestrator.infer(file.getBytes(), windowPreset);
    }

    @PostMapping(path = "/infer-raw", consumes = "application/dicom")
    public InferResponse inferRaw(
        @RequestBody byte[] dicomBytes,
        @RequestParam(defaultValue = "chest") String windowPreset
    ) {
        return orchestrator.infer(dicomBytes, windowPreset);
    }
}
