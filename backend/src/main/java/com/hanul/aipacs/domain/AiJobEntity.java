package com.hanul.aipacs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_jobs")
public class AiJobEntity {
    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AiJobStatus status;

    @Column(name = "study_instance_uid", nullable = false)
    private String studyInstanceUid;

    @Column(name = "series_instance_uid", nullable = false)
    private String seriesInstanceUid;

    @Column(name = "sop_instance_uid", nullable = false)
    private String sopInstanceUid;

    @Column(name = "result_series_instance_uid")
    private String resultSeriesInstanceUid;

    @Column(name = "result_sop_instance_uid")
    private String resultSopInstanceUid;

    @Column(name = "model_provider")
    private String modelProvider;

    @Column(name = "finding_label")
    private String findingLabel;

    private Double score;

    @Column(name = "qc_status")
    private String qcStatus;

    @Column(name = "stow_status")
    private String stowStatus;

    @Column(name = "readback_status")
    private String readbackStatus;

    @Column(name = "readback_verified_at")
    private Instant readbackVerifiedAt;

    @Column(name = "readback_error_message")
    private String readbackErrorMessage;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "result_json", columnDefinition = "TEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public AiJobStatus getStatus() {
        return status;
    }

    public void setStatus(AiJobStatus status) {
        this.status = status;
    }

    public String getStudyInstanceUid() {
        return studyInstanceUid;
    }

    public void setStudyInstanceUid(String studyInstanceUid) {
        this.studyInstanceUid = studyInstanceUid;
    }

    public String getSeriesInstanceUid() {
        return seriesInstanceUid;
    }

    public void setSeriesInstanceUid(String seriesInstanceUid) {
        this.seriesInstanceUid = seriesInstanceUid;
    }

    public String getSopInstanceUid() {
        return sopInstanceUid;
    }

    public void setSopInstanceUid(String sopInstanceUid) {
        this.sopInstanceUid = sopInstanceUid;
    }

    public String getResultSeriesInstanceUid() {
        return resultSeriesInstanceUid;
    }

    public void setResultSeriesInstanceUid(String resultSeriesInstanceUid) {
        this.resultSeriesInstanceUid = resultSeriesInstanceUid;
    }

    public String getResultSopInstanceUid() {
        return resultSopInstanceUid;
    }

    public void setResultSopInstanceUid(String resultSopInstanceUid) {
        this.resultSopInstanceUid = resultSopInstanceUid;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getFindingLabel() {
        return findingLabel;
    }

    public void setFindingLabel(String findingLabel) {
        this.findingLabel = findingLabel;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    public String getStowStatus() {
        return stowStatus;
    }

    public void setStowStatus(String stowStatus) {
        this.stowStatus = stowStatus;
    }

    public String getReadbackStatus() {
        return readbackStatus;
    }

    public void setReadbackStatus(String readbackStatus) {
        this.readbackStatus = readbackStatus;
    }

    public Instant getReadbackVerifiedAt() {
        return readbackVerifiedAt;
    }

    public void setReadbackVerifiedAt(Instant readbackVerifiedAt) {
        this.readbackVerifiedAt = readbackVerifiedAt;
    }

    public String getReadbackErrorMessage() {
        return readbackErrorMessage;
    }

    public void setReadbackErrorMessage(String readbackErrorMessage) {
        this.readbackErrorMessage = readbackErrorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
