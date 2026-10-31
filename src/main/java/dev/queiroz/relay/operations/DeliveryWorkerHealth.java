package dev.queiroz.relay.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component("deliveryWorker")
public class DeliveryWorkerHealth implements HealthIndicator {
    private final Clock clock;
    private final Duration staleAfter;
    private volatile Instant lastHeartbeat;

    public DeliveryWorkerHealth(Clock clock, @Value("${relay.worker.health-stale-after:PT1M}") Duration staleAfter) {
        this.clock = clock; this.staleAfter = staleAfter;
    }

    public void heartbeat() { lastHeartbeat = clock.instant(); }

    @Override public Health health() {
        Instant heartbeat = lastHeartbeat;
        if (heartbeat == null) return Health.outOfService().withDetail("reason", "worker has not claimed work yet").build();
        long age = Duration.between(heartbeat, clock.instant()).toSeconds();
        return age <= staleAfter.toSeconds() ? Health.up().withDetail("heartbeatAgeSeconds", age).build() : Health.down().withDetail("heartbeatAgeSeconds", age).build();
    }
}
