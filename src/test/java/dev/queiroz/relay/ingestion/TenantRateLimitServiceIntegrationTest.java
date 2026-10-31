package dev.queiroz.relay.ingestion;

import dev.queiroz.relay.ingestion.application.TenantRateLimitService;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class TenantRateLimitServiceIntegrationTest {
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("rate_limits").withUsername("test").withPassword("test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword);
        r.add("relay.limits.ingestion-requests-per-minute", () -> 2); r.add("relay.limits.events-per-tenant-per-day", () -> 2);
    }
    @Autowired TenantRateLimitService limits;

    @Test void atomicallyCapsTenantBurstAndKeepsTenantsIndependent() {
        TenantId first = new TenantId("first"), second = new TenantId("second");
        assertTrue(limits.allowRequest(first)); assertTrue(limits.allowRequest(first)); assertFalse(limits.allowRequest(first));
        assertTrue(limits.allowRequest(second));
    }

    @Test void atomicallyCapsDailyEventQuota() {
        TenantId tenant = new TenantId("quota-tenant");
        assertTrue(limits.reserveDailyEvent(tenant)); assertTrue(limits.reserveDailyEvent(tenant)); assertFalse(limits.reserveDailyEvent(tenant));
    }
}
