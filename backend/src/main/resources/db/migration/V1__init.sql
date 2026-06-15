CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE ai_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(64) NOT NULL,
    study_instance_uid VARCHAR(96) NOT NULL,
    series_instance_uid VARCHAR(96) NOT NULL,
    sop_instance_uid VARCHAR(96) NOT NULL,
    result_series_instance_uid VARCHAR(96),
    result_sop_instance_uid VARCHAR(96),
    model_provider VARCHAR(64),
    finding_label VARCHAR(160),
    score DOUBLE PRECISION,
    qc_status VARCHAR(32),
    stow_status VARCHAR(64),
    error_message TEXT,
    result_json TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE qc_reports (
    id UUID PRIMARY KEY,
    study_instance_uid VARCHAR(96) NOT NULL,
    series_instance_uid VARCHAR(96) NOT NULL,
    sop_instance_uid VARCHAR(96) NOT NULL,
    status VARCHAR(32) NOT NULL,
    report_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor VARCHAR(120) NOT NULL,
    action VARCHAR(120) NOT NULL,
    resource_type VARCHAR(80) NOT NULL,
    resource_id VARCHAR(160) NOT NULL,
    outcome VARCHAR(80) NOT NULL,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE stored_artifacts (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_jobs(id) ON DELETE CASCADE,
    artifact_type VARCHAR(80) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    bytes BYTEA NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ai_jobs_study ON ai_jobs(study_instance_uid);
CREATE INDEX idx_qc_reports_instance ON qc_reports(study_instance_uid, series_instance_uid, sop_instance_uid);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at DESC);
CREATE INDEX idx_stored_artifacts_job_type ON stored_artifacts(job_id, artifact_type);
