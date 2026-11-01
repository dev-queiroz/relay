package dev.queiroz.relay.status.application;

import dev.queiroz.relay.api.pagination.AttemptCursor;
import dev.queiroz.relay.api.pagination.KeysetCursor;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

@Repository public class StatusStore {
 private final JdbcTemplate jdbc; public StatusStore(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Optional<EventSummary> event(TenantId t,UUID id){return jdbc.query("SELECT id,event_type,status,created_at FROM events WHERE tenant_id=? AND id=?",(r,n)->new EventSummary(r.getObject(1,UUID.class),r.getString(2),r.getString(3),instant(r,4)),t.value(),id).stream().findFirst();}
 public List<EventSummary> events(TenantId t,KeysetCursor c,int l){return jdbc.query("SELECT id,event_type,status,created_at FROM events WHERE tenant_id=? AND (CAST(? AS timestamptz) IS NULL OR created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC LIMIT ?",(r,n)->new EventSummary(r.getObject(1,UUID.class),r.getString(2),r.getString(3),instant(r,4)),t.value(),time(c),time(c),time(c),id(c),l);}
 public Optional<DeliverySummary> delivery(TenantId t,UUID id){return jdbc.query("SELECT id,event_id,endpoint_id,state,attempts_count,next_attempt_at,created_at,updated_at FROM deliveries WHERE tenant_id=? AND id=?",(r,n)->delivery(r),t.value(),id).stream().findFirst();}
 public List<DeliverySummary> deliveries(TenantId t,UUID event,KeysetCursor c,int l){return jdbc.query("SELECT id,event_id,endpoint_id,state,attempts_count,next_attempt_at,created_at,updated_at FROM deliveries WHERE tenant_id=? AND event_id=? AND (CAST(? AS timestamptz) IS NULL OR created_at<? OR (created_at=? AND id<?)) ORDER BY created_at DESC,id DESC LIMIT ?",(r,n)->delivery(r),t.value(),event,time(c),time(c),time(c),id(c),l);}
 public List<AttemptSummary> attempts(TenantId t,UUID delivery,AttemptCursor c,int l){return jdbc.query("SELECT a.id,a.delivery_id,a.sequence_number,a.started_at,a.finished_at,a.outcome,a.response_status,a.sanitized_error_code FROM delivery_attempts a JOIN deliveries d ON d.id=a.delivery_id WHERE d.tenant_id=? AND a.delivery_id=? AND (CAST(? AS integer) IS NULL OR a.sequence_number>? OR (a.sequence_number=? AND a.id>?)) ORDER BY a.sequence_number ASC,a.id ASC LIMIT ?",(r,n)->new AttemptSummary(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getInt(3),instant(r,4),nullable(r,5),r.getString(6),(Integer)r.getObject(7),r.getString(8)),t.value(),delivery,c==null?null:c.sequenceNumber(),c==null?null:c.sequenceNumber(),c==null?null:c.sequenceNumber(),c==null?null:c.id(),l);}
 private DeliverySummary delivery(java.sql.ResultSet r)throws java.sql.SQLException{return new DeliverySummary(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getInt(5),nullable(r,6),instant(r,7),instant(r,8));}
 private Instant instant(java.sql.ResultSet r,int n)throws java.sql.SQLException{return r.getObject(n,OffsetDateTime.class).toInstant();} private Instant nullable(java.sql.ResultSet r,int n)throws java.sql.SQLException{OffsetDateTime v=r.getObject(n,OffsetDateTime.class);return v==null?null:v.toInstant();} private Timestamp time(KeysetCursor c){return c==null?null:Timestamp.from(c.createdAt());} private UUID id(KeysetCursor c){return c==null?null:c.id();}
 public record EventSummary(UUID id,String type,String status,Instant createdAt){} public record DeliverySummary(UUID id,UUID eventId,UUID endpointId,String state,int attemptsCount,Instant nextAttemptAt,Instant createdAt,Instant updatedAt){} public record AttemptSummary(UUID id,UUID deliveryId,int sequenceNumber,Instant startedAt,Instant finishedAt,String outcome,Integer responseStatus,String sanitizedErrorCode){}
}
