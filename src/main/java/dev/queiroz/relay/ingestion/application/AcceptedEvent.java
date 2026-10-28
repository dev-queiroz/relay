package dev.queiroz.relay.ingestion.application;

import dev.queiroz.relay.ingestion.domain.EventId;

import java.util.Objects;

public record AcceptedEvent(EventId eventId) {

    public AcceptedEvent {
        Objects.requireNonNull(eventId, "event ID is required");
    }
}
