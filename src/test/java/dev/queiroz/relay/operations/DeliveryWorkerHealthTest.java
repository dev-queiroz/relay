package dev.queiroz.relay.operations;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DeliveryWorkerHealthTest {
    @Test void isOutOfServiceBeforeTheFirstClaimThenBecomesHealthy() {
        DeliveryWorkerHealth health = new DeliveryWorkerHealth(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(1), true);
        assertEquals("OUT_OF_SERVICE", health.health().getStatus().getCode());
        health.heartbeat();
        assertEquals("UP", health.health().getStatus().getCode());
    }
    @Test void isHealthyWhenWorkerHealthIsNotRequired() { assertEquals("UP", new DeliveryWorkerHealth(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), Duration.ofMinutes(1), false).health().getStatus().getCode()); }
}
