CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE ai_jobs (
    id CHAR(36) PRIMARY KEY,
    status VARCHAR(64) NOT NULL,
    study_instance_uid VARCHAR(96) NOT NULL,
    series_instance_uid VARCHAR(96) NOT NULL,
    sop_instance_uid VARCHAR(96) NOT NULL,
    result_series_instance_uid VARCHAR(96),
    result_sop_instance_uid VARCHAR(96),
    model_provider VARCHAR(64),
    finding_label VARCHAR(160),
    score DOUBLE,
    qc_status VARCHAR(32),
    stow_status VARCHAR(64),
    error_message TEXT,
    result_json TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE qc_reports (
    id CHAR(36) PRIMARY KEY,
    study_instance_uid VARCHAR(96) NOT NULL,
    series_instance_uid VARCHAR(96) NOT NULL,
    sop_instance_uid VARCHAR(96) NOT NULL,
    status VARCHAR(32) NOT NULL,
    report_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE audit_logs (
    id CHAR(36) PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    outcome VARCHAR(80) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE TABLE stored_artifacts (
    id CHAR(36) PRIMARY KEY,
    job_id CHAR(36) NOT NULL,
    artifact_type VARCHAR(80) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    bytes LONGBLOB NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_stored_artifacts_job FOREIGN KEY (job_id) REFERENCES ai_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_jobs_study ON ai_jobs(study_instance_uid);
CREATE INDEX idx_qc_reports_instance ON qc_reports(study_instance_uid, series_instance_uid, sop_instance_uid);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_stored_artifacts_job_type ON stored_artifacts(job_id, artifact_type);
