package dev.queiroz.relay.ingestion.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IdempotencyRecord(
        IdempotencyRecordId id,
        TenantId tenantId,
        String key,
        String requestFingerprint,
        EventId eventId,
        Instant createdAt
) {

    public IdempotencyRecord {
        Objects.requireNonNull(id, "idempotency record ID is required");
        Objects.requireNonNull(tenantId, "tenant ID is required");
        validateRequired(key, "idempotency key");
        validateRequired(requestFingerprint, "request fingerprint");
        Objects.requireNonNull(eventId, "event ID is required");
        Objects.requireNonNull(createdAt, "idempotency record creation timestamp is required");
    }

    public static IdempotencyRecord create(
            TenantId tenantId,
            String key,
            String requestFingerprint,
            EventId eventId,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock is required");
        return new IdempotencyRecord(
                new IdempotencyRecordId(UUID.randomUUID()),
                tenantId,
                key,
                requestFingerprint,
                eventId,
                Instant.now(clock)
        );
    }

    private static void validateRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
