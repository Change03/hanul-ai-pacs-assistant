package com.hanul.aipacs.repository;

import com.hanul.aipacs.domain.AiJobEventEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobEventRepository extends JpaRepository<AiJobEventEntity, UUID> {
    List<AiJobEventEntity> findByJob_IdOrderByCreatedAtAsc(UUID jobId);
}
