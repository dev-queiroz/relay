package dev.queiroz.relay.delivery;

import dev.queiroz.relay.delivery.application.EndpointStore;
import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.application.AcceptEventCommand;
import dev.queiroz.relay.ingestion.application.EventAcceptanceService;
import dev.queiroz.relay.ingestion.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest @Testcontainers
class DeliverySchedulingIntegrationTest {
 @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("scheduling").withUsername("test").withPassword("test");
 @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); }
 @Autowired EventAcceptanceService acceptance; @Autowired EndpointStore endpoints; @Autowired JdbcTemplate jdbc;
 @Test void schedulesOnlyEnabledSameTenantEndpointsAndNeverReschedulesRetry() {
  TenantId tenant = new TenantId(UUID.randomUUID().toString()); TenantId other = new TenantId(UUID.randomUUID().toString());
  endpoints.create(new Endpoint(UUID.randomUUID(), tenant, "on", URI.create("http://localhost:8081/on"), true, Instant.now()));
  endpoints.create(new Endpoint(UUID.randomUUID(), tenant, "off", URI.create("http://localhost:8081/off"), false, Instant.now()));
  endpoints.create(new Endpoint(UUID.randomUUID(), other, "other", URI.create("http://localhost:8081/other"), true, Instant.now()));
  AcceptEventCommand command = new AcceptEventCommand(tenant, UUID.randomUUID().toString(), "a".repeat(64), new EventType("payment.confirmed"), EventPayload.from(JsonNodeFactory.instance.objectNode()));
  acceptance.accept(command); acceptance.accept(command);
  assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM deliveries WHERE tenant_id = ?", Integer.class, tenant.value()));
 }
}
