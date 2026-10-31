-- Supports tenant-bound endpoint listing in the contract's stable keyset order.
CREATE INDEX idx_endpoints_tenant_created_id
    ON endpoints (tenant_id, created_at DESC, id DESC);
