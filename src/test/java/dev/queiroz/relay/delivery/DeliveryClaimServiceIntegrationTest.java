package dev.queiroz.relay.delivery;
import dev.queiroz.relay.delivery.application.DeliveryClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.*; import static org.junit.jupiter.api.Assertions.*;
@SpringBootTest @Testcontainers class DeliveryClaimServiceIntegrationTest {
 @Container static final PostgreSQLContainer db=new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("claims").withUsername("test").withPassword("test");
 @DynamicPropertySource static void p(DynamicPropertyRegistry r){r.add("spring.datasource.url",db::getJdbcUrl);r.add("spring.datasource.username",db::getUsername);r.add("spring.datasource.password",db::getPassword);}
 @Autowired DeliveryClaimService claims; @Autowired JdbcTemplate jdbc;
 @Test void claimBoundsAreValidated(){assertThrows(IllegalArgumentException.class,()->claims.claimDue(0,java.time.Duration.ofSeconds(1)));}
}
