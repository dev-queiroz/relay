-- This migration is immutable after it reaches a shared environment.
CREATE TABLE events (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACCEPTED',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_events_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_events_status_accepted CHECK (status = 'ACCEPTED')
);

CREATE TABLE idempotency_records (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_idempotency_records_tenant_key UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT fk_idempotency_records_event
        FOREIGN KEY (tenant_id, event_id) REFERENCES events (tenant_id, id)
);

-- Supports tenant-scoped event history queries in creation order.
CREATE INDEX idx_events_tenant_created_at ON events (tenant_id, created_at DESC);

-- Supports JSONB containment queries without forcing a payload schema into columns.
CREATE INDEX idx_events_payload ON events USING GIN (payload);
