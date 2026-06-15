package com.hanul.aipacs.repository;

import com.hanul.aipacs.domain.QcReportEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QcReportRepository extends JpaRepository<QcReportEntity, UUID> {
    long countByStatus(String status);

    List<QcReportEntity> findTop20ByOrderByCreatedAtDesc();

    Optional<QcReportEntity> findTopByStudyInstanceUidAndSeriesInstanceUidAndSopInstanceUidOrderByCreatedAtDesc(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid
    );

    Optional<QcReportEntity> findTopByStudyInstanceUidOrderByCreatedAtDesc(String studyInstanceUid);
}
