package dev.queiroz.relay.ingestion;

import dev.queiroz.relay.ingestion.application.AcceptEventCommand;
import dev.queiroz.relay.ingestion.application.AcceptedEvent;
import dev.queiroz.relay.ingestion.application.EventAcceptanceService;
import dev.queiroz.relay.ingestion.application.IdempotencyConflictException;
import dev.queiroz.relay.ingestion.domain.EventPayload;
import dev.queiroz.relay.ingestion.domain.EventType;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.JsonNodeFactory;

import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class EventAcceptanceIntegrationTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_event_store")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EventAcceptanceService eventAcceptanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsEventAndIdempotencyRecordAtomically() {
        TenantId tenant = tenant();
        AcceptedEvent accepted = eventAcceptanceService.accept(command(tenant, "key-1", FINGERPRINT));

        assertEquals(1, count("events", tenant));
        assertEquals(1, count("idempotency_records", tenant));
        assertEquals(
                accepted.eventId().value(),
                jdbcTemplate.queryForObject(
                        "SELECT event_id FROM idempotency_records WHERE tenant_id = ? AND idempotency_key = ?",
                        UUID.class,
                        tenant.value(),
                        "key-1"
                )
        );
    }

    @Test
    void returnsOriginalEventForIdenticalRetryWithoutAddingRows() {
        TenantId tenant = tenant();
        AcceptEventCommand command = command(tenant, "key-1", FINGERPRINT);

        AcceptedEvent first = eventAcceptanceService.accept(command);
        AcceptedEvent retry = eventAcceptanceService.accept(command);

        assertEquals(first.eventId(), retry.eventId());
        assertEquals(1, count("events", tenant));
        assertEquals(1, count("idempotency_records", tenant));
    }

    @Test
    void rejectsSameKeyWithDifferentFingerprint() {
        TenantId tenant = tenant();
        eventAcceptanceService.accept(command(tenant, "key-1", FINGERPRINT));

        assertThrows(
                IdempotencyConflictException.class,
                () -> eventAcceptanceService.accept(command(tenant, "key-1", "b".repeat(64)))
        );
        assertEquals(1, count("events", tenant));
        assertEquals(1, count("idempotency_records", tenant));
    }

    @Test
    void concurrentIdenticalSubmissionsCreateOneEvent() throws Exception {
        TenantId tenant = tenant();
        AcceptEventCommand command = command(tenant, "key-1", FINGERPRINT);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AcceptedEvent> first = executor.submit(submission(command, ready, start));
            Future<AcceptedEvent> second = executor.submit(submission(command, ready, start));
            ready.await();
            start.countDown();

            assertEquals(first.get().eventId(), second.get().eventId());
        }

        assertEquals(1, count("events", tenant));
        assertEquals(1, count("idempotency_records", tenant));
    }

    @Test
    void rollsBackEventWhenDatabaseRejectsIdempotencyRecord() {
        TenantId tenant = tenant();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> eventAcceptanceService.accept(command(tenant, "key-1", "a".repeat(65)))
        );

        assertEquals(0, count("events", tenant));
        assertEquals(0, count("idempotency_records", tenant));
    }

    private Callable<AcceptedEvent> submission(
            AcceptEventCommand command,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return eventAcceptanceService.accept(command);
        };
    }

    private AcceptEventCommand command(TenantId tenant, String key, String fingerprint) {
        return new AcceptEventCommand(
                tenant,
                key,
                fingerprint,
                new EventType("payment.confirmed"),
                EventPayload.from(JsonNodeFactory.instance.objectNode().put("paymentId", "pay_123"))
        );
    }

    private TenantId tenant() {
        return new TenantId(UUID.randomUUID().toString());
    }

    private int count(String table, TenantId tenant) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE tenant_id = ?",
                Integer.class,
                tenant.value()
        );
    }
}
