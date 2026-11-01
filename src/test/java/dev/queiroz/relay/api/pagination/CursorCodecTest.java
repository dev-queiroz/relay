package dev.queiroz.relay.api.pagination;

import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CursorCodecTest {
    private static final String KEY = "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXowMTIzNDU2Nzg=";
    private static final Instant NOW = Instant.parse("2026-10-31T22:00:00Z");

    @Test
    void bindsCursorToRouteAndTenantAndRejectsTampering() {
        CursorCodec codec = new CursorCodec(KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        KeysetCursor cursor = new KeysetCursor(NOW.minusSeconds(1), UUID.randomUUID());
        String token = codec.encode("v1/endpoints", new TenantId("tenant-a"), cursor);

        assertEquals(cursor, codec.decode(token, "v1/endpoints", new TenantId("tenant-a")));
        assertThrows(InvalidCursorException.class, () -> codec.decode(token, "v1/events", new TenantId("tenant-a")));
        assertThrows(InvalidCursorException.class, () -> codec.decode(token, "v1/endpoints", new TenantId("tenant-b")));
        assertThrows(InvalidCursorException.class, () -> codec.decode(token + "x", "v1/endpoints", new TenantId("tenant-a")));
    }

    @Test
    void expiresAfterTwentyFourHours() {
        CursorCodec issuing = new CursorCodec(KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        String token = issuing.encode("v1/endpoints", new TenantId("tenant-a"), new KeysetCursor(NOW, UUID.randomUUID()));
        CursorCodec expired = new CursorCodec(KEY, Clock.offset(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24)));

        assertThrows(InvalidCursorException.class, () -> expired.decode(token, "v1/endpoints", new TenantId("tenant-a")));
    }

    @Test
    void protectsAttemptOrderingCursorToo() {
        CursorCodec codec = new CursorCodec(KEY, Clock.fixed(NOW, ZoneOffset.UTC));
        AttemptCursor cursor = new AttemptCursor(2, UUID.randomUUID());
        String token = codec.encodeAttempt("v1/deliveries/a/attempts", new TenantId("tenant-a"), cursor);
        assertEquals(cursor, codec.decodeAttempt(token, "v1/deliveries/a/attempts", new TenantId("tenant-a")));
        assertThrows(InvalidCursorException.class, () -> codec.decodeAttempt(token, "v1/deliveries/b/attempts", new TenantId("tenant-a")));
    }
}
