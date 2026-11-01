CREATE INDEX idx_events_tenant_created_id ON events (tenant_id, created_at DESC, id DESC);
CREATE INDEX idx_deliveries_tenant_event_created_id ON deliveries (tenant_id, event_id, created_at DESC, id DESC);
CREATE INDEX idx_delivery_attempts_delivery_sequence_id ON delivery_attempts (delivery_id, sequence_number ASC, id ASC);
