package dev.queiroz.relay.delivery.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookSignatureTest {
    private final WebhookSignature signatures = new WebhookSignature(Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC));
    private final String secret = "endpoint-secret";
    private final String timestamp = "1700000000";

    @Test void signsAndVerifiesTheExactOutboundBytes() {
        byte[] body = "{\"amount\":10}".getBytes(StandardCharsets.UTF_8);
        String signature = signatures.sign(secret, timestamp, body);
        assertTrue(signatures.verify(secret, timestamp, signature, body, Duration.ofMinutes(5)));
        assertFalse(signatures.verify(secret, timestamp, signature, "{\"amount\":11}".getBytes(StandardCharsets.UTF_8), Duration.ofMinutes(5)));
    }

    @Test void rejectsReplayOutsideTheTimestampWindowAndMalformedHeaders() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertFalse(signatures.verify(secret, "1699999000", signatures.sign(secret, "1699999000", body), body, Duration.ofMinutes(5)));
        assertFalse(signatures.verify(secret, "", "v1=anything", body, Duration.ofMinutes(5)));
        assertFalse(signatures.verify(secret, timestamp, "", body, Duration.ofMinutes(5)));
    }

    @Test void rejectsRotatedOrIncorrectSecrets() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String signature = signatures.sign(secret, timestamp, body);
        assertFalse(signatures.verify("rotated-secret", timestamp, signature, body, Duration.ofMinutes(5)));
    }
}
