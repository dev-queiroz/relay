CREATE TABLE tenant_request_windows (
    tenant_id VARCHAR(64) NOT NULL,
    window_started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    request_count INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, window_started_at),
    CONSTRAINT chk_tenant_request_count_positive CHECK (request_count > 0)
);

CREATE TABLE tenant_event_quota_days (
    tenant_id VARCHAR(64) NOT NULL,
    quota_day DATE NOT NULL,
    event_count INTEGER NOT NULL,
    PRIMARY KEY (tenant_id, quota_day),
    CONSTRAINT chk_tenant_event_count_positive CHECK (event_count > 0)
);
