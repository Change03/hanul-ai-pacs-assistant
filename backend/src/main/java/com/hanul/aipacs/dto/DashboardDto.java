package com.hanul.aipacs.dto;

import java.util.List;
import java.util.Map;

public record DashboardDto(
    long studies,
    long aiJobsCompleted,
    long qcWarnings,
    long failedJobs,
    Map<String, String> health,
    List<AuditDto> recentAudit
) {
}
