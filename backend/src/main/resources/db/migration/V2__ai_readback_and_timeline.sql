ALTER TABLE ai_jobs
    ADD COLUMN readback_status VARCHAR(80),
    ADD COLUMN readback_verified_at TIMESTAMP(6),
    ADD COLUMN readback_error_message TEXT;

CREATE TABLE ai_job_events (
    id CHAR(36) PRIMARY KEY,
    job_id CHAR(36) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    status VARCHAR(80) NOT NULL,
    message TEXT NOT NULL,
    details_json TEXT,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_ai_job_events_job FOREIGN KEY (job_id) REFERENCES ai_jobs(id) ON DELETE CASCADE
);

CREATE INDEX idx_ai_job_events_job_created ON ai_job_events(job_id, created_at ASC);
