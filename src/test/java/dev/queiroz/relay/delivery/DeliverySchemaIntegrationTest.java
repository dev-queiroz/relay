package dev.queiroz.relay.delivery;

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

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class DeliverySchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("test_delivery_schema")
            .withUsername("test_user")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesV2AfterV1AndCreatesDeliveryTables() {
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '2' AND success = true", Integer.class
        ));
        assertEquals(3, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('endpoints', 'deliveries', 'delivery_attempts')
                """, Integer.class
        ));
    }

    @Test
    void rejectsCrossTenantDeliveryAndAllowsDifferentEndpointsForOneEvent() {
        String tenantA = tenant();
        String tenantB = tenant();
        UUID event = event(tenantA);
        UUID endpointA = endpoint(tenantA);
        UUID endpointB = endpoint(tenantB);

        assertDoesNotThrow(() -> delivery(tenantA, event, endpointA));
        assertThrows(DataIntegrityViolationException.class, () -> delivery(tenantA, event, endpointB));

        UUID secondEndpointA = endpoint(tenantA);
        assertDoesNotThrow(() -> delivery(tenantA, event, secondEndpointA));
    }

    @Test
    void rejectsRepeatedEventEndpointAndInvalidDeliveryStates() {
        String tenant = tenant();
        UUID event = event(tenant);
        UUID endpoint = endpoint(tenant);
        delivery(tenant, event, endpoint);

        assertThrows(DataIntegrityViolationException.class, () -> delivery(tenant, event, endpoint));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO deliveries (id, tenant_id, event_id, endpoint_id, destination_url, state, attempts_count, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, 'UNKNOWN', 0, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), tenant, event, endpoint(tenant), "http://fixture.test/invalid"
        ));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO deliveries (id, tenant_id, event_id, endpoint_id, destination_url, state, attempts_count, next_attempt_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', -1, CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), tenant, event, endpoint(tenant), "http://fixture.test/invalid"
        ));
    }

    @Test
    void enforcesAttemptForeignKeySequenceAndCompletionConstraints() {
        String tenant = tenant();
        UUID delivery = delivery(tenant, event(tenant), endpoint(tenant));

        attempt(delivery, 1);
        assertThrows(DataIntegrityViolationException.class, () -> attempt(delivery, 1));
        assertThrows(DataIntegrityViolationException.class, () -> attempt(UUID.randomUUID(), 1));
        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO delivery_attempts (id, delivery_id, sequence_number, started_at, outcome)
                VALUES (?, ?, 2, CURRENT_TIMESTAMP, 'SUCCEEDED')
                """, UUID.randomUUID(), delivery
        ));
    }

    @Test
    void createsDueWorkAndExpiredLeaseIndexes() {
        assertEquals(2, jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname IN ('idx_deliveries_due_work', 'idx_deliveries_expired_leases')
                """, Integer.class
        ));
    }

    private UUID event(String tenant) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO events (id, tenant_id, event_type, payload) VALUES (?, ?, 'payment.confirmed', '{}'::jsonb)",
                id, tenant
        );
        return id;
    }

    private UUID endpoint(String tenant) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO endpoints (id, tenant_id, name, destination_url) VALUES (?, ?, 'fixture', 'http://fixture.test/hook')",
                id, tenant
        );
        return id;
    }

    private UUID delivery(String tenant, UUID event, UUID endpoint) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO deliveries (id, tenant_id, event_id, endpoint_id, destination_url, state, attempts_count, next_attempt_at)
                VALUES (?, ?, ?, ?, 'http://fixture.test/hook', 'PENDING', 0, CURRENT_TIMESTAMP)
                """, id, tenant, event, endpoint
        );
        return id;
    }

    private void attempt(UUID delivery, int sequence) {
        jdbcTemplate.update(
                "INSERT INTO delivery_attempts (id, delivery_id, sequence_number) VALUES (?, ?, ?)",
                UUID.randomUUID(), delivery, sequence
        );
    }

    private String tenant() {
        return UUID.randomUUID().toString();
    }
}
