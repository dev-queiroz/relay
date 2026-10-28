package dev.queiroz.relay.ingestion.domain;

import java.util.Objects;
import java.util.regex.Pattern;

public record EventType(String value) {

    private static final Pattern VALID_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    public EventType {
        Objects.requireNonNull(value, "event type is required");
        if (!VALID_FORMAT.matcher(value).matches()) {
            throw new InvalidEventException(
                    "event type must be 1-128 ASCII letters, digits, dots, underscores, or hyphens"
            );
        }
    }
}
