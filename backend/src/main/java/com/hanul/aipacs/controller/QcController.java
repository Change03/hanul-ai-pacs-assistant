package com.hanul.aipacs.controller;

import com.hanul.aipacs.client.OrthancClient;
import com.hanul.aipacs.dto.QcDtos.QcReportDto;
import com.hanul.aipacs.dto.QcDtos.QcValidateRequest;
import com.hanul.aipacs.service.AuditService;
import com.hanul.aipacs.service.QcService;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/qc")
public class QcController {
    private final OrthancClient orthancClient;
    private final QcService qcService;
    private final AuditService auditService;

    public QcController(OrthancClient orthancClient, QcService qcService, AuditService auditService) {
        this.orthancClient = orthancClient;
        this.qcService = qcService;
        this.auditService = auditService;
    }

    @PostMapping("/validate")
    public QcReportDto validate(@RequestBody QcValidateRequest request, Authentication authentication) {
        byte[] bytes = orthancClient.getInstanceDicomBytes(request.studyInstanceUid(), request.seriesInstanceUid(), request.sopInstanceUid());
        QcReportDto report = qcService.validateAndSave(bytes, request.studyInstanceUid(), request.seriesInstanceUid(), request.sopInstanceUid());
        auditService.record(actor(authentication), "QC_VALIDATE", "INSTANCE", request.sopInstanceUid(), report.status().name(), Map.of("source", "orthanc"));
        return report;
    }

    @PostMapping(value = "/validate-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public QcReportDto validateUpload(@RequestPart("file") MultipartFile file, Authentication authentication) throws Exception {
        QcReportDto report = qcService.validateDicom(file.getBytes());
        auditService.record(actor(authentication), "QC_VALIDATE_UPLOAD", "FILE", file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename(), report.status().name(), Map.of("bytes", file.getSize()));
        return report;
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "anonymous" : authentication.getName();
    }
}
