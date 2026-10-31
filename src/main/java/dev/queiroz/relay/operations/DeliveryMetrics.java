package dev.queiroz.relay.operations;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMetrics {
    private final Counter accepted;
    private final Counter attempts;
    private final Counter succeeded;
    private final Counter failed;

    public DeliveryMetrics(MeterRegistry registry, JdbcTemplate jdbc) {
        accepted = Counter.builder("relay.events.accepted").description("Durably accepted events").register(registry);
        attempts = Counter.builder("relay.delivery.attempts").description("Outbound delivery attempts").register(registry);
        succeeded = Counter.builder("relay.delivery.succeeded").description("Successful outbound deliveries").register(registry);
        failed = Counter.builder("relay.delivery.failed").description("Failed outbound deliveries").register(registry);
        Gauge.builder("relay.delivery.queued", jdbc, value -> count(value, "SELECT count(*) FROM deliveries WHERE state IN ('PENDING','RETRY_WAIT')")).register(registry);
        Gauge.builder("relay.delivery.leased", jdbc, value -> count(value, "SELECT count(*) FROM deliveries WHERE state = 'IN_FLIGHT'")).register(registry);
        Gauge.builder("relay.delivery.backlog.age", jdbc, value -> oldestQueueAgeSeconds(value)).description("Age in seconds of the oldest queued delivery").register(registry);
    }

    public void eventAccepted() { accepted.increment(); }
    public void attemptStarted() { attempts.increment(); }
    public void deliverySucceeded() { succeeded.increment(); }
    public void deliveryFailed() { failed.increment(); }

    private double count(JdbcTemplate jdbc, String sql) {
        try { return jdbc.queryForObject(sql, Long.class); } catch (RuntimeException exception) { return Double.NaN; }
    }
    private double oldestQueueAgeSeconds(JdbcTemplate jdbc) {
        try {
            Double age = jdbc.queryForObject("SELECT EXTRACT(EPOCH FROM CURRENT_TIMESTAMP - min(created_at)) FROM deliveries WHERE state IN ('PENDING','RETRY_WAIT')", Double.class);
            return age == null ? 0 : age;
        } catch (RuntimeException exception) { return Double.NaN; }
    }
}
