package dev.queiroz.relay.ingestion.domain;

import java.util.Objects;

public record TenantId(String value) {

    public TenantId {
        Objects.requireNonNull(value, "tenant ID is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException("tenant ID must not be blank");
        }
    }
}
