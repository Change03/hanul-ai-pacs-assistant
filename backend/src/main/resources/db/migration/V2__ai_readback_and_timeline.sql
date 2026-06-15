ALTER TABLE ai_jobs
    ADD COLUMN readback_status VARCHAR(80),
    ADD COLUMN readback_verified_at TIMESTAMPTZ,
    ADD COLUMN readback_error_message TEXT;

CREATE TABLE ai_job_events (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES ai_jobs(id) ON DELETE CASCADE,
    event_type VARCHAR(120) NOT NULL,
    status VARCHAR(80) NOT NULL,
    message TEXT NOT NULL,
    details_json TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_ai_job_events_job_created ON ai_job_events(job_id, created_at ASC);
