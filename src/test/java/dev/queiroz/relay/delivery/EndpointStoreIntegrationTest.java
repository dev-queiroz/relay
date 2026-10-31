package dev.queiroz.relay.delivery;

import dev.queiroz.relay.delivery.application.EndpointStore;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
class EndpointStoreIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("endpoints").withUsername("test").withPassword("test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); }
    @Autowired EndpointStore store;
    @Test void isolatesTenantsAndExcludesDisabledEndpoints() {
        TenantId first = new TenantId(UUID.randomUUID().toString()); TenantId second = new TenantId(UUID.randomUUID().toString()); UUID id = UUID.randomUUID();
        store.create(new Endpoint(id, first, "first", URI.create("http://localhost:8081/a"), true, Instant.now()));
        store.create(new Endpoint(UUID.randomUUID(), second, "second", URI.create("http://localhost:8081/b"), true, Instant.now()));
        assertTrue(store.find(first, id).isPresent()); assertTrue(store.find(second, id).isEmpty()); assertEquals(1, store.enabledFor(first).size());
        assertTrue(store.setEnabled(first, id, false)); assertTrue(store.enabledFor(first).isEmpty()); assertFalse(store.setEnabled(second, id, true));
    }
}
