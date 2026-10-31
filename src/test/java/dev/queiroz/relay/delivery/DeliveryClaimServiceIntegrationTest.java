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
 @Test void claimsDueWorkOnceAndFencesLateFinalizers(){
  UUID id=delivery("PENDING",0,null); var first=claims.claimDue(1,java.time.Duration.ofMinutes(1)); var second=claims.claimDue(1,java.time.Duration.ofMinutes(1));
  assertEquals(1,first.size()); assertTrue(second.isEmpty()); assertFalse(claims.finalize(id,UUID.randomUUID(),"SUCCEEDED",java.time.Instant.now())); assertTrue(claims.finalize(id,first.getFirst().leaseToken(),"SUCCEEDED",java.time.Instant.now()));
 }
 @Test void reclaimsExpiredLease(){ UUID id=delivery("IN_FLIGHT",1,java.time.Instant.now().minusSeconds(1)); var claimed=claims.claimDue(1,java.time.Duration.ofMinutes(1)); assertEquals(id,claimed.getFirst().deliveryId()); assertEquals(2,claimed.getFirst().attemptNumber()); }
 @Test void exhaustsExpiredFifthLeaseBeforeApplyingClaimLimit(){ UUID exhausted=delivery("IN_FLIGHT",5,java.time.Instant.now().minusSeconds(1)); UUID pending=delivery("PENDING",0,null); var claimed=claims.claimDue(1,java.time.Duration.ofMinutes(1)); assertEquals(pending,claimed.getFirst().deliveryId()); assertEquals("EXHAUSTED",jdbc.queryForObject("SELECT state FROM deliveries WHERE id=?",String.class,exhausted)); }
 @Test void claimBoundsAreValidated(){assertThrows(IllegalArgumentException.class,()->claims.claimDue(0,java.time.Duration.ofSeconds(1)));}
 @Test void claimsAtMostOneDeliveryForAnEndpointAtATime(){
  String tenant=UUID.randomUUID().toString(); UUID endpoint=UUID.randomUUID();
  jdbc.update("INSERT INTO endpoints (id,tenant_id,name,destination_url) VALUES (?,?, 'x','http://localhost:8081/x')",endpoint,tenant);
  for(int i=0;i<2;i++){ UUID event=UUID.randomUUID(), delivery=UUID.randomUUID(); jdbc.update("INSERT INTO events (id,tenant_id,event_type,payload) VALUES (?,?, 'x','{}'::jsonb)",event,tenant); jdbc.update("INSERT INTO deliveries (id,tenant_id,event_id,endpoint_id,destination_url,state,attempts_count,next_attempt_at) VALUES (?,?,?,?,?,'PENDING',0,CURRENT_TIMESTAMP - interval '1 second')",delivery,tenant,event,endpoint,"http://localhost:8081/x"); }
  assertEquals(1,claims.claimDue(2,java.time.Duration.ofMinutes(1)).size());
 }
 private UUID delivery(String state,int attempts,java.time.Instant leaseExpiry){
  String tenant=UUID.randomUUID().toString(); UUID event=UUID.randomUUID(), endpoint=UUID.randomUUID(), delivery=UUID.randomUUID();
  jdbc.update("INSERT INTO events (id,tenant_id,event_type,payload) VALUES (?,?, 'x','{}'::jsonb)",event,tenant);
  jdbc.update("INSERT INTO endpoints (id,tenant_id,name,destination_url) VALUES (?,?, 'x','http://localhost:8081/x')",endpoint,tenant);
  jdbc.update("INSERT INTO deliveries (id,tenant_id,event_id,endpoint_id,destination_url,state,attempts_count,next_attempt_at,lease_token,lease_expires_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP - interval '1 second',?,?)",delivery,tenant,event,endpoint,"http://localhost:8081/x",state,attempts,leaseExpiry==null?null:UUID.randomUUID(),leaseExpiry==null?null:java.sql.Timestamp.from(leaseExpiry)); return delivery;
 }
}
