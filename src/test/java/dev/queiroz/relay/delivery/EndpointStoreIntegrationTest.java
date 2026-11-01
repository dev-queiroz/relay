package dev.queiroz.relay.delivery;

import dev.queiroz.relay.delivery.application.EndpointStore;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class EndpointStoreIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("endpoints").withUsername("test").withPassword("test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); }
    @Autowired EndpointStore store;
    @Autowired JdbcTemplate jdbc;
    @Test void isolatesTenantsAndExcludesDisabledEndpoints() {
        TenantId first = new TenantId(UUID.randomUUID().toString()); TenantId second = new TenantId(UUID.randomUUID().toString()); UUID id = UUID.randomUUID();
        store.create(new Endpoint(id, first, "first", URI.create("http://localhost:8081/a"), true, Instant.now()));
        store.create(new Endpoint(UUID.randomUUID(), second, "second", URI.create("http://localhost:8081/b"), true, Instant.now()));
        assertTrue(store.find(first, id).isPresent()); assertTrue(store.find(second, id).isEmpty()); assertEquals(1, store.enabledFor(first).size());
        assertTrue(store.setEnabled(first, id, false)); assertTrue(store.enabledFor(first).isEmpty()); assertFalse(store.setEnabled(second, id, true));
    }

    @Test void keysetPageIsTenantScopedAndUpdateLeavesDeliveryDestinationSnapshotUntouched() {
        TenantId tenant = new TenantId(UUID.randomUUID().toString());
        Instant oldest = Instant.parse("2026-01-01T00:00:00Z");
        Endpoint first = new Endpoint(UUID.randomUUID(), tenant, "first", URI.create("https://public.example/old"), true, oldest);
        Endpoint second = new Endpoint(UUID.randomUUID(), tenant, "second", URI.create("https://public.example/second"), true, oldest.plusSeconds(1));
        Endpoint third = new Endpoint(UUID.randomUUID(), tenant, "third", URI.create("https://public.example/third"), true, oldest.plusSeconds(2));
        store.create(first); store.create(second); store.create(third);

        List<Endpoint> firstPage = store.page(tenant, null, null, 2);
        assertEquals(List.of(third.id(), second.id()), firstPage.stream().map(Endpoint::id).toList());
        assertEquals(List.of(first.id()), store.page(tenant, firstPage.getLast().createdAt(), firstPage.getLast().id(), 2).stream().map(Endpoint::id).toList());

        UUID eventId = UUID.randomUUID();
        UUID deliveryId = UUID.randomUUID();
        jdbc.update("INSERT INTO events (id,tenant_id,event_type,payload) VALUES (?,?, 'fixture','{}'::jsonb)", eventId, tenant.value());
        jdbc.update("""
                INSERT INTO deliveries (id,tenant_id,event_id,endpoint_id,destination_url,state,attempts_count,next_attempt_at)
                VALUES (?,?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP)
                """, deliveryId, tenant.value(), eventId, first.id(), first.destinationUrl().toString());

        Endpoint updated = store.update(tenant, first.id(), "renamed", URI.create("https://public.example/new"), false).orElseThrow();
        assertEquals("renamed", updated.name());
        assertFalse(updated.enabled());
        assertEquals("https://public.example/new", updated.destinationUrl().toString());
        assertEquals("https://public.example/old", jdbc.queryForObject("SELECT destination_url FROM deliveries WHERE id=?", String.class, deliveryId));
    }
}
