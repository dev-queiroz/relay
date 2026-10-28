package dev.queiroz.relay.ingestion.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IdempotencyRecordTest {

    @Test
    void suppliesRecordIdAndUtcCreationTimestamp() {
        Instant now = Instant.parse("2026-10-28T21:00:00Z");
        IdempotencyRecord record = IdempotencyRecord.create(
                new TenantId("tenant-123"),
                "8d411c50-3ebd-4b7c-a4e3-137d13780d34",
                "a".repeat(64),
                new EventId(UUID.randomUUID()),
                Clock.fixed(now, ZoneOffset.UTC)
        );

        assertNotNull(record.id().value());
        assertEquals(now, record.createdAt());
        assertEquals("tenant-123", record.tenantId().value());
    }
}
