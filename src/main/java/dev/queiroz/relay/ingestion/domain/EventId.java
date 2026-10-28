package dev.queiroz.relay.ingestion.domain;

import java.util.Objects;
import java.util.UUID;

public record EventId(UUID value) {

    public EventId {
        Objects.requireNonNull(value, "event ID is required");
    }
}
