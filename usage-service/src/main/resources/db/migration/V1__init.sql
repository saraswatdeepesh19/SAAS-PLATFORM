CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE usage_records (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    device_id UUID NOT NULL,
    session_id UUID NOT NULL UNIQUE,
    duration_sec BIGINT NOT NULL CHECK (duration_sec >= 0),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_usage_rec_tenant_time ON usage_records (tenant_id, ended_at);

CREATE TABLE usage_monthly (
    tenant_id UUID NOT NULL,
    period CHAR(7) NOT NULL,
    total_seconds BIGINT NOT NULL DEFAULT 0,
    total_sessions BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (tenant_id, period)
);