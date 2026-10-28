package dev.queiroz.relay.ingestion.domain;

import java.util.Objects;
import java.util.UUID;

public record IdempotencyRecordId(UUID value) {

    public IdempotencyRecordId {
        Objects.requireNonNull(value, "idempotency record ID is required");
    }
}
