package com.hanul.aipacs.service;

import com.hanul.aipacs.client.AiClient;
import com.hanul.aipacs.client.OrthancClient;
import com.hanul.aipacs.dto.StudyDtos.InstanceMetadata;
import com.hanul.aipacs.dto.StudyDtos.InstanceSummary;
import com.hanul.aipacs.dto.StudyDtos.SeriesSummary;
import com.hanul.aipacs.dto.StudyDtos.StudySummary;
import com.hanul.aipacs.repository.AiJobRepository;
import com.hanul.aipacs.repository.QcReportRepository;
import com.hanul.aipacs.service.DicomLiteParser.ParsedDicom;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyService {
    private final OrthancClient orthancClient;
    private final AiClient aiClient;
    private final AiJobRepository aiJobs;
    private final QcReportRepository qcReports;
    private final AuditService auditService;

    public StudyService(OrthancClient orthancClient, AiClient aiClient, AiJobRepository aiJobs, QcReportRepository qcReports, AuditService auditService) {
        this.orthancClient = orthancClient;
        this.aiClient = aiClient;
        this.aiJobs = aiJobs;
        this.qcReports = qcReports;
        this.auditService = auditService;
    }

    public List<StudySummary> listStudies(String actor) {
        List<StudySummary> studies = orthancClient.listStudies().stream()
            .map(study -> new StudySummary(
                study.patientId(),
                study.studyDate(),
                study.modality(),
                study.studyDescription(),
                study.studyInstanceUid(),
                study.numberOfSeries(),
                aiJobs.findTopByStudyInstanceUidOrderByCreatedAtDesc(study.studyInstanceUid()).map(job -> job.getStatus().name()).orElse("NOT_RUN"),
                qcReports.findTopByStudyInstanceUidOrderByCreatedAtDesc(study.studyInstanceUid()).map(qc -> qc.getStatus()).orElse("NOT_RUN")
            ))
            .toList();
        auditService.record(actor, "DICOM_QIDO_STUDIES", "STUDY", "ALL", "SUCCESS", Map.of("count", studies.size()));
        return studies;
    }

    public StudySummary getStudy(String studyUid, String actor) {
        return listStudies(actor).stream()
            .filter(study -> study.studyInstanceUid().equals(studyUid))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Orthanc에서 검사를 찾을 수 없습니다: " + studyUid));
    }

    public List<SeriesSummary> listSeries(String studyUid, String actor) {
        List<SeriesSummary> series = orthancClient.listSeries(studyUid);
        auditService.record(actor, "DICOM_QIDO_SERIES", "STUDY", studyUid, "SUCCESS", Map.of("count", series.size()));
        return series;
    }

    public List<InstanceSummary> listInstances(String studyUid, String seriesUid, String actor) {
        List<InstanceSummary> instances = orthancClient.listInstances(studyUid, seriesUid);
        auditService.record(actor, "DICOM_QIDO_INSTANCES", "SERIES", seriesUid, "SUCCESS", Map.of("count", instances.size()));
        return instances;
    }

    public List<InstanceSummary> listStudyInstances(String studyUid, String actor) {
        return listSeries(studyUid, actor).stream()
            .flatMap(series -> listInstances(studyUid, series.seriesInstanceUid(), actor).stream())
            .toList();
    }

    public InstanceMetadata metadata(String studyUid, String seriesUid, String sopUid, String actor) {
        byte[] bytes = orthancClient.getInstanceDicomBytes(studyUid, seriesUid, sopUid);
        ParsedDicom parsed = DicomLiteParser.parse(bytes);
        Map<String, Object> metadata = parsed.metadata();
        auditService.record(actor, "DICOM_METADATA", "INSTANCE", sopUid, "SUCCESS", Map.of("byteLength", bytes.length));
        return new InstanceMetadata(metadata);
    }

    public byte[] preview(String studyUid, String seriesUid, String sopUid, String window, String actor) {
        byte[] bytes = orthancClient.getInstanceDicomBytes(studyUid, seriesUid, sopUid);
        byte[] png = aiClient.renderPreview(bytes, window);
        auditService.record(actor, "DICOM_PREVIEW", "INSTANCE", sopUid, "SUCCESS", Map.of("window", window == null ? "auto" : window));
        return png;
    }

    public byte[] dicom(String studyUid, String seriesUid, String sopUid, String actor) {
        byte[] bytes = orthancClient.getInstanceDicomBytes(studyUid, seriesUid, sopUid);
        auditService.record(actor, "DICOM_WADO_RETRIEVE", "INSTANCE", sopUid, "SUCCESS", Map.of("bytes", bytes.length));
        return bytes;
    }
}
