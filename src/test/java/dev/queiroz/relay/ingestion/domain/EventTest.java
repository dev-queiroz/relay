package dev.queiroz.relay.ingestion.domain;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EventTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final TenantId TENANT = new TenantId("tenant-123");
    private static final Instant NOW = Instant.parse("2026-10-28T21:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void acceptsValidTypeAndObjectPayload() {
        Event event = Event.accept(
                TENANT,
                new EventType("payment.confirmed-v2_1"),
                EventPayload.from(objectPayload()),
                CLOCK
        );

        assertNotNull(event.id().value());
        assertEquals(TENANT, event.tenantId());
        assertEquals("payment.confirmed-v2_1", event.type().value());
        assertEquals(NOW, event.createdAt());
        assertEquals(EventStatus.ACCEPTED, event.status());
    }

    @Test
    void acceptsOneAndOneHundredTwentyEightCharacterTypes() {
        assertEquals("a", new EventType("a").value());
        assertEquals(128, new EventType("a".repeat(128)).value().length());
    }

    @Test
    void rejectsMissingBlankAndInvalidEventTypes() {
        assertThrows(NullPointerException.class, () -> new EventType(null));
        assertThrows(InvalidEventException.class, () -> new EventType(""));
        assertThrows(InvalidEventException.class, () -> new EventType("   "));
        assertThrows(InvalidEventException.class, () -> new EventType("a".repeat(129)));
        assertThrows(InvalidEventException.class, () -> new EventType("payment/confirmed"));
        assertThrows(InvalidEventException.class, () -> new EventType("pagamento.confirmado-á"));
    }

    @Test
    void rejectsMissingAndNonObjectPayloads() {
        assertThrows(NullPointerException.class, () -> EventPayload.from(null));
        assertThrows(InvalidEventException.class, () -> EventPayload.from(JSON.arrayNode()));
        assertThrows(InvalidEventException.class, () -> EventPayload.from(JSON.stringNode("value")));
        assertThrows(InvalidEventException.class, () -> EventPayload.from(JSON.nullNode()));
    }

    @Test
    void copiesPayloadToPreserveEventImmutability() {
        ObjectNode source = objectPayload();
        Event event = Event.accept(TENANT, new EventType("payment.confirmed"), EventPayload.from(source), CLOCK);

        source.put("amount", 999);
        JsonNode returnedPayload = event.payload().value();
        ((ObjectNode) returnedPayload).put("amount", 888);

        assertEquals(100, event.payload().value().get("amount").asInt());
    }

    private ObjectNode objectPayload() {
        return JSON.objectNode().put("amount", 100);
    }
}
