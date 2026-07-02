package com.hanul.aipacs.repository;

import com.hanul.aipacs.domain.AiJobEntity;
import com.hanul.aipacs.domain.enums.AiJobStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJobEntity, UUID> {
    long countByStatus(AiJobStatus status);

    List<AiJobEntity> findTop20ByOrderByCreatedAtDesc();

    Optional<AiJobEntity> findTopByStudyInstanceUidAndSeriesInstanceUidAndSopInstanceUidOrderByCreatedAtDesc(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid
    );

    Optional<AiJobEntity> findTopByStudyInstanceUidOrderByCreatedAtDesc(String studyInstanceUid);
}
