package dev.queiroz.relay.ingestion.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Event(
        EventId id,
        TenantId tenantId,
        EventType type,
        EventPayload payload,
        Instant createdAt,
        EventStatus status
) {

    public Event {
        Objects.requireNonNull(id, "event ID is required");
        Objects.requireNonNull(tenantId, "tenant ID is required");
        Objects.requireNonNull(type, "event type is required");
        Objects.requireNonNull(payload, "event payload is required");
        Objects.requireNonNull(createdAt, "event creation timestamp is required");
        Objects.requireNonNull(status, "event status is required");
    }

    public static Event accept(
            TenantId tenantId,
            EventType type,
            EventPayload payload,
            Clock clock
    ) {
        Objects.requireNonNull(clock, "clock is required");
        return new Event(
                new EventId(UUID.randomUUID()),
                tenantId,
                type,
                payload,
                Instant.now(clock),
                EventStatus.ACCEPTED
        );
    }
}
