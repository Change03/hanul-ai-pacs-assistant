package com.hanul.aipacs.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditDto(
    UUID id,
    String actor,
    String action,
    String resourceType,
    String resourceId,
    String outcome,
    Map<String, Object> details,
    Instant createdAt
) {
}
