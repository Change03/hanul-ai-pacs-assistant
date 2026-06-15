package com.hanul.aipacs.repository;

import com.hanul.aipacs.domain.AuditLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    List<AuditLogEntity> findTop100ByOrderByCreatedAtDesc();

    List<AuditLogEntity> findTop10ByOrderByCreatedAtDesc();
}
