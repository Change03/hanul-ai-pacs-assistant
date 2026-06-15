package com.hanul.aipacs.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hanul.aipacs.domain.AuditLogEntity;
import com.hanul.aipacs.dto.AuditDto;
import com.hanul.aipacs.repository.AuditLogRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditLogRepository auditLogs;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogs, ObjectMapper objectMapper) {
        this.auditLogs = auditLogs;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(String actor, String action, String resourceType, String resourceId, String outcome, Map<String, Object> details) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActor(actor == null || actor.isBlank() ? "anonymous" : actor);
        entity.setAction(action);
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setOutcome(outcome);
        entity.setDetailsJson(writeJson(details == null ? Map.of() : details));
        auditLogs.save(entity);
    }

    @Transactional(readOnly = true)
    public List<AuditDto> latest() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditDto> recentDashboard() {
        return auditLogs.findTop10ByOrderByCreatedAtDesc().stream().map(this::toDto).toList();
    }

    private AuditDto toDto(AuditLogEntity entity) {
        return new AuditDto(
            entity.getId(),
            entity.getActor(),
            entity.getAction(),
            entity.getResourceType(),
            entity.getResourceId(),
            entity.getOutcome(),
            readJson(entity.getDetailsJson()),
            entity.getCreatedAt()
        );
    }

    private String writeJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
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
            return Map.of("unparsed", json);
        }
    }
}
