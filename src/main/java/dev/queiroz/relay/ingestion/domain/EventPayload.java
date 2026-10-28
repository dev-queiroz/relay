package dev.queiroz.relay.ingestion.domain;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Objects;

public final class EventPayload {

    private final ObjectNode value;

    private EventPayload(ObjectNode value) {
        this.value = value.deepCopy();
    }

    public static EventPayload from(JsonNode value) {
        Objects.requireNonNull(value, "event payload is required");
        if (!value.isObject()) {
            throw new InvalidEventException("event payload must be a JSON object");
        }
        return new EventPayload((ObjectNode) value);
    }

    public ObjectNode value() {
        return value.deepCopy();
    }
}
