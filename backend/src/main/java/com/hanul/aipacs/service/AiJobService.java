package com.hanul.aipacs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.client.AiClient;
import com.hanul.aipacs.client.OrthancClient;
import com.hanul.aipacs.domain.AiJobEntity;
import com.hanul.aipacs.domain.AiJobEventEntity;
import com.hanul.aipacs.domain.AiJobStatus;
import com.hanul.aipacs.domain.QcStatus;
import com.hanul.aipacs.domain.StoredArtifactEntity;
import com.hanul.aipacs.dto.AiDtos.AiInferResponse;
import com.hanul.aipacs.dto.AiDtos.AiJobCreateRequest;
import com.hanul.aipacs.dto.AiDtos.AiJobDto;
import com.hanul.aipacs.dto.AiDtos.AiJobEventDto;
import com.hanul.aipacs.dto.AiDtos.ResultDicomMetadata;
import com.hanul.aipacs.dto.QcDtos.QcReportDto;
import com.hanul.aipacs.repository.AiJobEventRepository;
import com.hanul.aipacs.repository.AiJobRepository;
import com.hanul.aipacs.repository.StoredArtifactRepository;
import com.hanul.aipacs.service.DicomLiteParser.ParsedDicom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiJobService {
    private static final String DISCLAIMER = "Demo only. Not for clinical use. No real patient data.";

    private final AiJobRepository aiJobs;
    private final AiJobEventRepository aiJobEvents;
    private final StoredArtifactRepository artifacts;
    private final OrthancClient orthancClient;
    private final AiClient aiClient;
    private final QcService qcService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    public AiJobService(
        AiJobRepository aiJobs,
        AiJobEventRepository aiJobEvents,
        StoredArtifactRepository artifacts,
        OrthancClient orthancClient,
        AiClient aiClient,
        QcService qcService,
        AuditService auditService,
        ObjectMapper objectMapper,
        @Qualifier("aiJobExecutor") Executor executor
    ) {
        this.aiJobs = aiJobs;
        this.aiJobEvents = aiJobEvents;
        this.artifacts = artifacts;
        this.orthancClient = orthancClient;
        this.aiClient = aiClient;
        this.qcService = qcService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.executor = executor;
    }

    public UUID createJob(AiJobCreateRequest request, String actor) {
        // 요청 시점에는 작업만 빠르게 저장하고, 실제 DICOM 처리와 AI 추론은 별도 executor에서 비동기로 수행한다.
        AiJobEntity job = new AiJobEntity();
        job.setStatus(AiJobStatus.QUEUED);
        job.setStudyInstanceUid(request.studyInstanceUid());
        job.setSeriesInstanceUid(request.seriesInstanceUid());
        job.setSopInstanceUid(request.sopInstanceUid());
        AiJobEntity saved = aiJobs.save(job);
        recordEvent(saved, "QUEUED", "QUEUED", "AI 작업이 대기열에 등록되었습니다.", Map.of(
            "studyInstanceUid", request.studyInstanceUid(),
            "seriesInstanceUid", request.seriesInstanceUid(),
            "sopInstanceUid", request.sopInstanceUid()
        ));
        auditService.record(actor, "AI_JOB_CREATE", "AI_JOB", saved.getId().toString(), "QUEUED", Map.of(
            "studyInstanceUid", request.studyInstanceUid(),
            "seriesInstanceUid", request.seriesInstanceUid(),
            "sopInstanceUid", request.sopInstanceUid()
        ));
        executor.execute(() -> runJob(saved.getId(), request.windowPreset(), actor));
        return saved.getId();
    }

    public void runJob(UUID jobId, String windowPreset, String actor) {
        AiJobEntity job = aiJobs.findById(jobId).orElseThrow();
        try {
            // 1) 원본 DICOM은 PACS(Orthanc)에서 다시 가져온다. 클라이언트가 보낸 바이트를 신뢰하지 않기 위한 흐름이다.
            job.setStatus(AiJobStatus.RUNNING);
            aiJobs.save(job);
            recordEvent(job, "DICOM_FETCH_STARTED", "RUNNING", "원본 DICOM을 Orthanc에서 가져오기 시작했습니다.", Map.of("sopInstanceUid", job.getSopInstanceUid()));
            auditService.record(actor, "DICOM_WADO_RETRIEVE", "INSTANCE", job.getSopInstanceUid(), "STARTED", Map.of("jobId", jobId.toString()));

            byte[] dicomBytes = orthancClient.getInstanceDicomBytes(job.getStudyInstanceUid(), job.getSeriesInstanceUid(), job.getSopInstanceUid());
            recordEvent(job, "DICOM_FETCHED_FROM_ORTHANC", "SUCCESS", "원본 DICOM을 Orthanc에서 가져왔습니다.", Map.of("bytes", dicomBytes.length));
            auditService.record(actor, "DICOM_WADO_RETRIEVE", "INSTANCE", job.getSopInstanceUid(), "SUCCESS", Map.of("bytes", dicomBytes.length));

            // 2) AI 호출 전에 QC Gate를 통과시킨다. FAIL이면 임상 안전성 데모 관점에서 추론을 차단한다.
            recordEvent(job, "QC_STARTED", "RUNNING", "AI 추론 전 QC 게이트를 실행했습니다.", Map.of());
            QcReportDto qcReport = qcService.validateAndSave(dicomBytes, job.getStudyInstanceUid(), job.getSeriesInstanceUid(), job.getSopInstanceUid());
            job.setQcStatus(qcReport.status().name());
            aiJobs.save(job);
            recordEvent(job, qcEvent(qcReport.status()), qcReport.status().name(), "QC 게이트 결과: " + qcReport.status(), Map.of("checks", qcReport.checks().size()));
            auditService.record(actor, "QC_VALIDATE", "INSTANCE", job.getSopInstanceUid(), qcReport.status().name(), Map.of("jobId", jobId.toString()));

            if (qcReport.status() == QcStatus.FAIL) {
                job.setStatus(AiJobStatus.BLOCKED_BY_QC);
                job.setErrorMessage("QC 상태가 실패라서 AI 추론을 차단했습니다.");
                aiJobs.save(job);
                recordEvent(job, "BLOCKED_BY_QC", "BLOCKED_BY_QC", "QC FAIL 때문에 AI 추론을 실행하지 않았습니다.", Map.of("qcStatus", "FAIL"));
                auditService.record(actor, "AI_INFERENCE_BLOCKED", "AI_JOB", jobId.toString(), "BLOCKED_BY_QC", Map.of("qcStatus", "FAIL"));
                return;
            }

            // 3) QC가 PASS/WARN인 경우에만 AI 서비스로 DICOM bytes를 전달한다.
            String normalizedWindow = windowPreset == null || windowPreset.isBlank() ? "chest" : windowPreset;
            recordEvent(job, "AI_INFERENCE_STARTED", "RUNNING", "AI 서비스에 DICOM 추론을 요청했습니다.", Map.of("windowPreset", normalizedWindow));
            AiInferResponse response = aiClient.infer(dicomBytes, normalizedWindow);
            byte[] overlay = Base64.getDecoder().decode(response.overlayPngBase64());
            byte[] heatmap = Base64.getDecoder().decode(response.heatmapPngBase64());
            byte[] resultDicom = Base64.getDecoder().decode(response.resultDicomBase64());
            ParsedDicom resultParsed = DicomLiteParser.parse(resultDicom);
            String provider = displayProvider(response.modelProvider());

            job.setModelProvider(provider);
            job.setFindingLabel(response.findingLabel());
            job.setScore(response.score());
            job.setResultSeriesInstanceUid(resultParsed.value("0020000E"));
            job.setResultSopInstanceUid(resultParsed.value("00080018"));
            job.setStowStatus("PENDING");
            job.setReadbackStatus("PENDING");
            job.setResultJson(writeResultJson(response, provider, qcReport, job, resultParsed, "PENDING", null));
            aiJobs.save(job);
            recordEvent(job, "AI_INFERENCE_COMPLETED", "SUCCESS", "AI 서비스가 데모 결과를 반환했습니다.", Map.of(
                "modelProvider", provider,
                "findingLabel", response.findingLabel(),
                "score", response.score()
            ));
            recordEvent(job, "RESULT_DICOM_CREATED", "SUCCESS", "Secondary Capture 결과 DICOM을 생성했습니다.", Map.of(
                "resultSeriesInstanceUid", safe(job.getResultSeriesInstanceUid()),
                "resultSopInstanceUid", safe(job.getResultSopInstanceUid())
            ));

            // 4) overlay/heatmap/result DICOM은 재조회와 다운로드가 가능하도록 DB artifact로 보관한다.
            saveArtifact(job, "OVERLAY_PNG", "image/png", overlay);
            saveArtifact(job, "HEATMAP_PNG", "image/png", heatmap);
            saveArtifact(job, "RESULT_DICOM", "application/dicom", resultDicom);
            auditService.record(actor, "AI_INFERENCE", "AI_JOB", jobId.toString(), "SUCCESS", Map.of(
                "modelProvider", provider,
                "findingLabel", response.findingLabel(),
                "score", response.score()
            ));

            // 5) 원본은 수정하지 않고, 생성된 Secondary Capture DICOM만 STOW-RS로 PACS에 저장한다.
            recordEvent(job, "STOW_UPLOAD_STARTED", "RUNNING", "생성된 결과 DICOM을 Orthanc에 STOW-RS로 업로드하기 시작했습니다.", Map.of());
            String stowStatus = orthancClient.stowDicom(resultDicom);
            job.setStowStatus(stowStatus);
            aiJobs.save(job);
            recordEvent(job, "STOW_UPLOADED", stowStatus, "결과 DICOM을 Orthanc에 STOW-RS로 업로드했습니다.", Map.of("stowStatus", stowStatus));
            auditService.record(actor, "DICOM_STOW_RESULT", "INSTANCE", job.getResultSopInstanceUid(), stowStatus, Map.of("jobId", jobId.toString()));

            verifyReadBack(job, response, provider, qcReport, resultParsed, actor, jobId);
        } catch (Exception ex) {
            job.setStatus(AiJobStatus.FAILED);
            job.setErrorMessage(truncate(ex.getMessage()));
            aiJobs.save(job);
            recordEvent(job, "FAILED", "FAILED", "AI 작업이 실패했습니다.", Map.of("message", truncate(ex.getMessage())));
            auditService.record(actor, "AI_JOB_FAILURE", "AI_JOB", jobId.toString(), "FAILED", Map.of("message", truncate(ex.getMessage())));
        }
    }

    private void verifyReadBack(
        AiJobEntity job,
        AiInferResponse response,
        String provider,
        QcReportDto qcReport,
        ParsedDicom resultParsed,
        String actor,
        UUID jobId
    ) {
        // STOW-RS는 업로드 요청 성공만 의미할 수 있으므로, 생성 UID로 PACS를 다시 조회해 저장 여부를 검증한다.
        recordEvent(job, "ORTHANC_READBACK_STARTED", "RUNNING", "STOW 업로드 후 Orthanc에서 결과 DICOM 존재 여부를 다시 확인했습니다.", Map.of(
            "studyInstanceUid", safe(job.getStudyInstanceUid()),
            "seriesInstanceUid", safe(job.getResultSeriesInstanceUid()),
            "sopInstanceUid", safe(job.getResultSopInstanceUid())
        ));

        boolean verified = false;
        String error = null;
        try {
            verified = orthancClient.verifyInstanceExists(job.getStudyInstanceUid(), job.getResultSeriesInstanceUid(), job.getResultSopInstanceUid());
        } catch (Exception ex) {
            error = truncate(ex.getMessage());
        }

        if (verified) {
            job.setReadbackStatus("READBACK_VERIFIED");
            job.setReadbackVerifiedAt(Instant.now());
            job.setReadbackErrorMessage(null);
            job.setStatus(AiJobStatus.COMPLETED_VERIFIED);
            job.setResultJson(writeResultJson(response, provider, qcReport, job, resultParsed, "READBACK_VERIFIED", null));
            aiJobs.save(job);
            recordEvent(job, "ORTHANC_READBACK_VERIFIED", "READBACK_VERIFIED", "Orthanc에서 생성된 결과 DICOM을 read-back으로 확인했습니다.", Map.of("verifiedAt", job.getReadbackVerifiedAt().toString()));
            recordEvent(job, "COMPLETED_VERIFIED", "COMPLETED_VERIFIED", "AI 작업이 STOW-RS 저장과 read-back 검증까지 완료되었습니다.", Map.of());
            auditService.record(actor, "ORTHANC_READBACK_VERIFIED", "INSTANCE", job.getResultSopInstanceUid(), "READBACK_VERIFIED", Map.of("jobId", jobId.toString()));
            return;
        }

        String message = error == null ? "STOW succeeded but Orthanc read-back did not find the generated instance." : error;
        job.setReadbackStatus("READBACK_FAILED");
        job.setReadbackErrorMessage(message);
        job.setStatus(AiJobStatus.COMPLETED_UNVERIFIED);
        job.setResultJson(writeResultJson(response, provider, qcReport, job, resultParsed, "READBACK_FAILED", message));
        aiJobs.save(job);
        recordEvent(job, "ORTHANC_READBACK_FAILED", "READBACK_FAILED", "STOW 업로드는 성공했지만 read-back 검증에 실패했습니다.", Map.of("message", message));
        recordEvent(job, "COMPLETED_UNVERIFIED", "COMPLETED_UNVERIFIED", "AI 작업은 완료되었지만 Orthanc read-back 검증은 실패했습니다.", Map.of());
        auditService.record(actor, "ORTHANC_READBACK_FAILED", "INSTANCE", job.getResultSopInstanceUid(), "READBACK_FAILED", Map.of("jobId", jobId.toString(), "message", message));
    }

    @Transactional(readOnly = true)
    public AiJobDto getJob(UUID jobId) {
        return aiJobs.findById(jobId).map(this::toDto).orElseThrow(() -> new IllegalArgumentException("AI 작업을 찾을 수 없습니다: " + jobId));
    }

    @Transactional(readOnly = true)
    public List<AiJobDto> latestJobs() {
        return aiJobs.findTop20ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public StoredArtifactEntity getArtifact(UUID jobId, String type) {
        return artifacts.findByJob_IdAndArtifactType(jobId, type)
            .orElseThrow(() -> new IllegalArgumentException("산출물을 찾을 수 없습니다: " + type + " / 작업 " + jobId));
    }

    @Transactional(readOnly = true)
    public ResultDicomMetadata getResultMetadata(UUID jobId) {
        StoredArtifactEntity artifact = getArtifact(jobId, "RESULT_DICOM");
        ParsedDicom parsed = DicomLiteParser.parse(artifact.getBytes());
        return new ResultDicomMetadata(
            parsed.value("0020000D"),
            parsed.value("0020000E"),
            parsed.value("00080018"),
            parsed.value("00080016"),
            parsed.value("00080060"),
            parsed.value("0008103E"),
            parsed.value("00204000"),
            parsed.value("00280010"),
            parsed.value("00280011"),
            parsed.value("00020010")
        );
    }

    private void saveArtifact(AiJobEntity job, String type, String contentType, byte[] bytes) {
        StoredArtifactEntity artifact = new StoredArtifactEntity();
        artifact.setJob(job);
        artifact.setArtifactType(type);
        artifact.setContentType(contentType);
        artifact.setBytes(bytes);
        artifacts.save(artifact);
    }

    private AiJobDto toDto(AiJobEntity job) {
        return new AiJobDto(
            job.getId(),
            job.getStatus(),
            job.getStudyInstanceUid(),
            job.getSeriesInstanceUid(),
            job.getSopInstanceUid(),
            job.getResultSeriesInstanceUid(),
            job.getResultSopInstanceUid(),
            job.getModelProvider(),
            job.getFindingLabel(),
            job.getScore(),
            job.getQcStatus(),
            job.getStowStatus(),
            job.getReadbackStatus(),
            job.getReadbackVerifiedAt(),
            job.getReadbackErrorMessage(),
            job.getErrorMessage(),
            readJson(job.getResultJson()),
            aiJobEvents.findByJob_IdOrderByCreatedAtAsc(job.getId()).stream().map(this::toEventDto).toList(),
            DISCLAIMER,
            false,
            true,
            job.getCreatedAt(),
            job.getUpdatedAt()
        );
    }

    private AiJobEventDto toEventDto(AiJobEventEntity event) {
        return new AiJobEventDto(
            event.getId(),
            event.getEventType(),
            event.getStatus(),
            event.getMessage(),
            readJson(event.getDetailsJson()),
            event.getCreatedAt()
        );
    }

    private String writeResultJson(AiInferResponse response, String provider, QcReportDto qcReport, AiJobEntity job, ParsedDicom resultParsed, String readbackStatus, String readbackError) {
        try {
            // 결과 JSON은 UI 표시와 audit review를 위한 요약본이다. 원본 DICOM bytes 자체는 artifact 테이블에 별도로 저장한다.
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("boxes", response.boxes());
            json.put("riskBand", riskBand(response.score()));
            json.put("preprocessing", response.preprocessing());
            json.put("warnings", response.warnings());
            json.put("qc", Map.of("status", qcReport.status(), "checks", qcReport.checks()));
            json.put("qcSummary", Map.of("status", qcReport.status(), "checkCount", qcReport.checks().size()));
            json.put("originalUids", uidMap(job.getStudyInstanceUid(), job.getSeriesInstanceUid(), job.getSopInstanceUid()));
            json.put("generatedResultUids", uidMap(resultParsed.value("0020000D"), resultParsed.value("0020000E"), resultParsed.value("00080018")));
            json.put("modelProvider", provider);
            json.put("stowStatus", safe(job.getStowStatus()));
            json.put("readBackStatus", readbackStatus);
            if (readbackError != null) {
                json.put("readBackErrorMessage", readbackError);
            }
            json.put("disclaimer", DISCLAIMER);
            json.put("disclaimerKo", "데모 전용입니다. 임상 진료에 사용하지 마세요. 실제 환자 데이터는 없습니다.");
            return objectMapper.writeValueAsString(json);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void recordEvent(AiJobEntity job, String eventType, String status, String message, Map<String, Object> details) {
        AiJobEventEntity event = new AiJobEventEntity();
        event.setJob(job);
        event.setEventType(eventType);
        event.setStatus(status);
        event.setMessage(message);
        event.setDetailsJson(writeJson(details == null ? Map.of() : details));
        aiJobEvents.save(event);
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }

    private static String qcEvent(QcStatus status) {
        return switch (status) {
            case PASS -> "QC_PASSED";
            case WARN -> "QC_WARNED";
            case FAIL -> "QC_FAILED";
        };
    }

    private static String displayProvider(String provider) {
        if ("ANTHROPIC".equals(provider)) {
            return "EXPERIMENTAL_ANTHROPIC";
        }
        return provider;
    }

    private static String riskBand(double score) {
        if (score >= 0.7) {
            return "HIGH_DEMO";
        }
        if (score >= 0.4) {
            return "MEDIUM_DEMO";
        }
        return "LOW_DEMO";
    }

    private static Map<String, Object> uidMap(String studyUid, String seriesUid, String sopUid) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("studyInstanceUid", safe(studyUid));
        value.put("seriesInstanceUid", safe(seriesUid));
        value.put("sopInstanceUid", safe(sopUid));
        return value;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        return message.length() > 900 ? message.substring(0, 900) : message;
    }
}
