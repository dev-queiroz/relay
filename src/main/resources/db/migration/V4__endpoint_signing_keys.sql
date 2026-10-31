CREATE TABLE endpoint_signing_keys (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    endpoint_id UUID NOT NULL,
    secret VARCHAR(128) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retired_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_endpoint_signing_keys_endpoint
        FOREIGN KEY (tenant_id, endpoint_id) REFERENCES endpoints (tenant_id, id)
);

CREATE UNIQUE INDEX uq_endpoint_signing_keys_active_endpoint
    ON endpoint_signing_keys (tenant_id, endpoint_id)
    WHERE retired_at IS NULL;
