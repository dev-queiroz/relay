package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.Event;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;

@Repository
public class DeliveryStore {
    private final JdbcTemplate jdbc;
    public DeliveryStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void schedule(Event event, Endpoint endpoint, Clock clock) {
        var now = clock.instant();
        jdbc.update("""
                INSERT INTO deliveries (id, tenant_id, event_id, endpoint_id, destination_url, state, attempts_count, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """, UUID.randomUUID(), event.tenantId().value(), event.id().value(), endpoint.id(), endpoint.destinationUrl().toString(), Timestamp.from(now), Timestamp.from(now), Timestamp.from(now));
    }
}
