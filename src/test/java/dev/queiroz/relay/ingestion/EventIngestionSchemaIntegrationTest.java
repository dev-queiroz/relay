package dev.queiroz.relay.ingestion;

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class EventIngestionSchemaIntegrationTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
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
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldApplyFlywayMigration() {
        Integer migrationCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '1' AND success = true
                """,
                Integer.class
        );

        assertNotNull(migrationCount);
        assertEquals(1, migrationCount);
        assertEquals(
                2,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = 'public'
                          AND table_name IN ('events', 'idempotency_records')
                        """,
                        Integer.class
                )
        );
    }

    @Test
    void shouldRejectDuplicateIdempotencyKeyWithinSameTenant() {
        String tenant = uniqueTenant();
        UUID eventId = insertEvent(tenant);
        String key = "req-12345";

        insertIdempotencyRecord(tenant, key, eventId);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdempotencyRecord(tenant, key, eventId)
        );
    }

    @Test
    void shouldAllowSameIdempotencyKeyAcrossDifferentTenants() {
        String tenantA = uniqueTenant();
        String tenantB = uniqueTenant();
        String key = "shared-key";

        UUID eventA = insertEvent(tenantA);
        UUID eventB = insertEvent(tenantB);

        assertDoesNotThrow(() -> {
            insertIdempotencyRecord(tenantA, key, eventA);
            insertIdempotencyRecord(tenantB, key, eventB);
        });
    }

    @Test
    void shouldRejectNonexistentEventReference() {
        String tenant = uniqueTenant();
        UUID nonexistentEventId = UUID.randomUUID();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdempotencyRecord(
                        tenant,
                        "missing-event",
                        nonexistentEventId
                )
        );
    }

    @Test
    void shouldRejectCrossTenantEventReference() {
        String tenantA = uniqueTenant();
        String tenantB = uniqueTenant();

        UUID eventFromTenantA = insertEvent(tenantA);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertIdempotencyRecord(
                        tenantB,
                        "cross-tenant-key",
                        eventFromTenantA
                )
        );
    }

    private UUID insertEvent(String tenantId) {
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO events
                    (id, tenant_id, event_type, payload)
                VALUES (?, ?, ?, ?::jsonb)
                """,
                eventId,
                tenantId,
                "OrderCreated",
                "{\"amount\":250.00,\"currency\":\"BRL\"}"
        );

        return eventId;
    }

    private void insertIdempotencyRecord(
            String tenantId,
            String key,
            UUID eventId
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, idempotency_key,
                     event_id, request_fingerprint)
                VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                tenantId,
                key,
                eventId,
                FINGERPRINT
        );
    }

    private String uniqueTenant() {
        return UUID.randomUUID().toString();
    }
}
