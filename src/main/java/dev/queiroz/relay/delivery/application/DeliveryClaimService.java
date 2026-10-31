package dev.queiroz.relay.delivery.application;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

@Service
public class DeliveryClaimService {
 private final JdbcTemplate jdbc; private final TransactionTemplate tx; private final Clock clock;
 public DeliveryClaimService(JdbcTemplate jdbc, TransactionTemplate tx, Clock clock) { this.jdbc=jdbc; this.tx=tx; this.clock=clock; }
 public List<ClaimedDelivery> claimDue(int limit, Duration lease) {
  if (limit < 1 || limit > 100 || lease.isNegative() || lease.isZero()) throw new IllegalArgumentException("invalid claim bounds");
  return tx.execute(s -> { var now=clock.instant();
   jdbc.update("UPDATE deliveries SET state = 'EXHAUSTED', lease_token = NULL, lease_expires_at = NULL, updated_at = ? WHERE state = 'IN_FLIGHT' AND lease_expires_at <= ? AND attempts_count >= 5", Timestamp.from(now), Timestamp.from(now));
   UUID token=UUID.randomUUID(); return jdbc.query("""
   WITH due AS (SELECT id FROM deliveries WHERE attempts_count < 5 AND ((state IN ('PENDING','RETRY_WAIT') AND next_attempt_at <= ?) OR (state='IN_FLIGHT' AND lease_expires_at <= ?)) ORDER BY next_attempt_at FOR UPDATE SKIP LOCKED LIMIT ?)
   UPDATE deliveries d SET state='IN_FLIGHT', lease_token=?, lease_expires_at=?, attempts_count=d.attempts_count+1, updated_at=? FROM due WHERE d.id=due.id
   RETURNING d.id, d.tenant_id, d.event_id, d.destination_url, d.attempts_count
   """, (rs,row)->new ClaimedDelivery(rs.getObject("id",UUID.class),rs.getString("tenant_id"),rs.getObject("event_id",UUID.class),rs.getString("destination_url"),rs.getInt("attempts_count"),token), Timestamp.from(now),Timestamp.from(now),limit,token,Timestamp.from(now.plus(lease)),Timestamp.from(now)); });
 }
 public boolean finalize(UUID id, UUID token, String state, java.time.Instant next) {
  if (!Set.of("SUCCEEDED","RETRY_WAIT","EXHAUSTED").contains(state)) throw new IllegalArgumentException("invalid final state");
  return jdbc.update("UPDATE deliveries SET state=?, next_attempt_at=?, lease_token=NULL, lease_expires_at=NULL, updated_at=? WHERE id=? AND state='IN_FLIGHT' AND lease_token=?",state,Timestamp.from(next),Timestamp.from(clock.instant()),id,token)==1;
 }
}
