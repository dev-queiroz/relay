package dev.queiroz.relay.ingestion.application;

import dev.queiroz.relay.ingestion.domain.EventPayload;
import dev.queiroz.relay.ingestion.domain.EventType;
import dev.queiroz.relay.ingestion.domain.TenantId;

import java.util.Objects;

public record AcceptEventCommand(
        TenantId tenantId,
        String idempotencyKey,
        String requestFingerprint,
        EventType type,
        EventPayload payload
) {

    public AcceptEventCommand {
        Objects.requireNonNull(tenantId, "tenant ID is required");
        requireNonBlank(idempotencyKey, "idempotency key");
        requireNonBlank(requestFingerprint, "request fingerprint");
        Objects.requireNonNull(type, "event type is required");
        Objects.requireNonNull(payload, "event payload is required");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
