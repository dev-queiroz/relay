package dev.queiroz.relay.delivery.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import dev.queiroz.relay.operations.OperationalLog;
import java.net.URI; import java.net.http.*; import java.time.Duration; import java.util.UUID; import java.nio.charset.StandardCharsets;

@Service
public class HttpDeliveryDispatcher {
 private final JdbcTemplate jdbc; private final HttpClient client; private final LocalTargetPolicy targets; private final EndpointSigningKeyStore keys; private final WebhookSignature signatures; private final OperationalLog logs;
 public HttpDeliveryDispatcher(JdbcTemplate jdbc, LocalTargetPolicy targets, EndpointSigningKeyStore keys, WebhookSignature signatures, OperationalLog logs) { this.jdbc=jdbc; this.targets=targets; this.keys=keys; this.signatures=signatures; this.logs=logs; this.client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build(); }
 public int dispatch(ClaimedDelivery delivery) throws Exception {
  logs.deliveryStarted(delivery.tenantId(),delivery.eventId(),delivery.deliveryId());
  URI destination=targets.validate(delivery.destinationUrl());
  var event=jdbc.queryForMap("SELECT id,event_type,payload,created_at FROM events WHERE tenant_id=? AND id=?",delivery.tenantId(),delivery.eventId()); UUID attempt=UUID.randomUUID();
  jdbc.update("INSERT INTO delivery_attempts (id,delivery_id,sequence_number) VALUES (?,?,?)",attempt,delivery.deliveryId(),delivery.attemptNumber());
  String body="{\"id\":\""+event.get("id")+"\",\"type\":\""+event.get("event_type")+"\",\"createdAt\":\""+event.get("created_at")+"\",\"payload\":"+event.get("payload")+"}"; byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
  String secret=keys.activeSecret(delivery.tenantId(),delivery.endpointId()).orElseThrow(()->new IllegalStateException("endpoint has no active signing secret")); String timestamp=signatures.timestamp(); String signature=signatures.sign(secret,timestamp,bytes);
  try { var response=client.send(HttpRequest.newBuilder(destination).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("X-Relay-Delivery-Id",delivery.deliveryId().toString()).header("X-Relay-Event-Id",delivery.eventId().toString()).header("X-Relay-Attempt",Integer.toString(delivery.attemptNumber())).header("X-Relay-Timestamp",timestamp).header("X-Relay-Signature",signature).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.discarding()); jdbc.update("UPDATE delivery_attempts SET finished_at=CURRENT_TIMESTAMP,outcome=?,response_status=? WHERE id=?",response.statusCode()/100==2?"SUCCEEDED":"RETRYABLE_FAILURE",response.statusCode(),attempt); logs.deliveryCompleted(delivery.tenantId(),delivery.eventId(),delivery.deliveryId(),response.statusCode()); return response.statusCode(); }
  catch (Exception e) { jdbc.update("UPDATE delivery_attempts SET finished_at=CURRENT_TIMESTAMP,outcome='RETRYABLE_FAILURE',sanitized_error_code='TRANSPORT_ERROR' WHERE id=?",attempt); logs.deliveryFailed(delivery.tenantId(),delivery.eventId(),delivery.deliveryId()); throw e; }
 }
}
