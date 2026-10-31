-- M2 persistence foundation. Destination snapshots are mandatory because a delivery
-- must retain the endpoint address that was active when it was scheduled.
CREATE TABLE endpoints (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    destination_url TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_endpoints_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT chk_endpoints_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT chk_endpoints_destination_url_not_blank CHECK (length(btrim(destination_url)) > 0)
);

CREATE TABLE deliveries (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    event_id UUID NOT NULL,
    endpoint_id UUID NOT NULL,
    destination_url TEXT NOT NULL,
    state VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempts_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_token UUID,
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_deliveries_event_endpoint UNIQUE (event_id, endpoint_id),
    CONSTRAINT fk_deliveries_event
        FOREIGN KEY (tenant_id, event_id) REFERENCES events (tenant_id, id),
    CONSTRAINT fk_deliveries_endpoint
        FOREIGN KEY (tenant_id, endpoint_id) REFERENCES endpoints (tenant_id, id),
    CONSTRAINT chk_deliveries_destination_url_not_blank CHECK (length(btrim(destination_url)) > 0),
    CONSTRAINT chk_deliveries_state CHECK (state IN (
        'PENDING', 'IN_FLIGHT', 'SUCCEEDED', 'RETRY_WAIT', 'EXHAUSTED'
    )),
    CONSTRAINT chk_deliveries_attempts_count CHECK (attempts_count BETWEEN 0 AND 5),
    CONSTRAINT chk_deliveries_state_attempts CHECK (
        (state = 'PENDING' AND attempts_count = 0)
        OR (state IN ('IN_FLIGHT', 'SUCCEEDED', 'RETRY_WAIT', 'EXHAUSTED') AND attempts_count BETWEEN 1 AND 5)
    ),
    CONSTRAINT chk_deliveries_lease CHECK (
        (state = 'IN_FLIGHT' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (state <> 'IN_FLIGHT' AND lease_token IS NULL AND lease_expires_at IS NULL)
    )
);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES deliveries (id),
    sequence_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(32),
    response_status INTEGER,
    sanitized_error_code VARCHAR(64),
    CONSTRAINT uq_delivery_attempts_delivery_sequence UNIQUE (delivery_id, sequence_number),
    CONSTRAINT chk_delivery_attempts_sequence CHECK (sequence_number BETWEEN 1 AND 5),
    CONSTRAINT chk_delivery_attempts_outcome CHECK (
        outcome IS NULL OR outcome IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'PERMANENT_FAILURE')
    ),
    CONSTRAINT chk_delivery_attempts_response_status CHECK (
        response_status IS NULL OR response_status BETWEEN 100 AND 599
    ),
    CONSTRAINT chk_delivery_attempts_completion CHECK (
        (finished_at IS NULL AND outcome IS NULL AND response_status IS NULL AND sanitized_error_code IS NULL)
        OR (finished_at IS NOT NULL AND outcome IS NOT NULL)
    )
);

-- Claimers filter due PENDING/RETRY_WAIT work by next_attempt_at; this partial index
-- avoids indexing terminal deliveries. Lease recovery scans only IN_FLIGHT expirations.
CREATE INDEX idx_deliveries_due_work
    ON deliveries (state, next_attempt_at)
    WHERE state IN ('PENDING', 'RETRY_WAIT');
CREATE INDEX idx_deliveries_expired_leases
    ON deliveries (lease_expires_at)
    WHERE state = 'IN_FLIGHT';
