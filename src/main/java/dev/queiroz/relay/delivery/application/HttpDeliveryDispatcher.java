package dev.queiroz.relay.delivery.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.net.URI; import java.net.http.*; import java.time.Duration; import java.util.UUID;

@Service
public class HttpDeliveryDispatcher {
 private final JdbcTemplate jdbc; private final HttpClient client;
 public HttpDeliveryDispatcher(JdbcTemplate jdbc) { this.jdbc=jdbc; this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(); }
 public int dispatch(ClaimedDelivery delivery) throws Exception {
  var event=jdbc.queryForMap("SELECT id,event_type,payload,created_at FROM events WHERE id=?",delivery.eventId()); UUID attempt=UUID.randomUUID();
  jdbc.update("INSERT INTO delivery_attempts (id,delivery_id,sequence_number) VALUES (?,?,?)",attempt,delivery.deliveryId(),delivery.attemptNumber());
  String body="{\"id\":\""+event.get("id")+"\",\"type\":\""+event.get("event_type")+"\",\"createdAt\":\""+event.get("created_at")+"\",\"payload\":"+event.get("payload")+"}";
  try { var response=client.send(HttpRequest.newBuilder(URI.create(delivery.destinationUrl())).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Relay-Delivery-Id",delivery.deliveryId().toString()).header("X-Relay-Event-Id",delivery.eventId().toString()).header("X-Relay-Attempt",Integer.toString(delivery.attemptNumber())).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding()); jdbc.update("UPDATE delivery_attempts SET finished_at=CURRENT_TIMESTAMP,outcome=?,response_status=? WHERE id=?",response.statusCode()/100==2?"SUCCEEDED":"RETRYABLE_FAILURE",response.statusCode(),attempt); return response.statusCode(); }
  catch (Exception e) { jdbc.update("UPDATE delivery_attempts SET finished_at=CURRENT_TIMESTAMP,outcome='RETRYABLE_FAILURE',sanitized_error_code='TRANSPORT_ERROR' WHERE id=?",attempt); throw e; }
 }
}
