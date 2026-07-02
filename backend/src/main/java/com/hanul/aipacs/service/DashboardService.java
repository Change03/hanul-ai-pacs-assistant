package com.hanul.aipacs.service;

import com.hanul.aipacs.client.AiClient;
import com.hanul.aipacs.client.OrthancClient;
import com.hanul.aipacs.domain.enums.AiJobStatus;
import com.hanul.aipacs.dto.DashboardDto;
import com.hanul.aipacs.repository.AiJobRepository;
import com.hanul.aipacs.repository.QcReportRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final OrthancClient orthancClient;
    private final AiClient aiClient;
    private final AiJobRepository aiJobs;
    private final QcReportRepository qcReports;
    private final AuditService auditService;

    public DashboardService(OrthancClient orthancClient, AiClient aiClient, AiJobRepository aiJobs, QcReportRepository qcReports, AuditService auditService) {
        this.orthancClient = orthancClient;
        this.aiClient = aiClient;
        this.aiJobs = aiJobs;
        this.qcReports = qcReports;
        this.auditService = auditService;
    }

    public Map<String, String> health() {
        Map<String, String> health = new LinkedHashMap<>();
        health.put("web", "UP");
        health.put("backend", "UP");
        try {
            health.put("orthanc", orthancClient.health() ? "UP" : "DOWN");
        } catch (Exception e) {
            health.put("orthanc", "DOWN");
        }
        try {
            Map<String, Object> ai = aiClient.health();
            health.put("aiService", ai == null ? "DOWN" : "UP:" + ai.getOrDefault("provider", "UNKNOWN"));
        } catch (Exception e) {
            health.put("aiService", "DOWN");
        }
        health.put("mysql", "UP");
        return health;
    }

    public DashboardDto dashboard() {
        long studies;
        try {
            studies = orthancClient.listStudies().size();
            auditService.record("system", "DICOM_QIDO_STUDY_COUNT", "STUDY", "ALL", "SUCCESS", Map.of("count", studies));
        } catch (Exception e) {
            studies = 0;
            auditService.record("system", "DICOM_QIDO_STUDY_COUNT", "STUDY", "ALL", "FAILED", Map.of("message", e.getMessage() == null ? "unknown" : e.getMessage()));
        }
        return new DashboardDto(
            studies,
            aiJobs.countByStatus(AiJobStatus.COMPLETED) + aiJobs.countByStatus(AiJobStatus.COMPLETED_VERIFIED) + aiJobs.countByStatus(AiJobStatus.COMPLETED_UNVERIFIED),
            qcReports.countByStatus("WARN"),
            aiJobs.countByStatus(AiJobStatus.FAILED),
            health(),
            auditService.recentDashboard()
        );
    }
}
