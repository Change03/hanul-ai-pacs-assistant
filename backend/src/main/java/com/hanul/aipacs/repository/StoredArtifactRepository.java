package com.hanul.aipacs.repository;

import com.hanul.aipacs.domain.StoredArtifactEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredArtifactRepository extends JpaRepository<StoredArtifactEntity, UUID> {
    Optional<StoredArtifactEntity> findByJob_IdAndArtifactType(UUID jobId, String artifactType);
}
