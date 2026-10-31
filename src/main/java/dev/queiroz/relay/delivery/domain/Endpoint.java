package dev.queiroz.relay.delivery.domain;

import dev.queiroz.relay.ingestion.domain.TenantId;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

public record Endpoint(UUID id, TenantId tenantId, String name, URI destinationUrl, boolean enabled, Instant createdAt) {
}
