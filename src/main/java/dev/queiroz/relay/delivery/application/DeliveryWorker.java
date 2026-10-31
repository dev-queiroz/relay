package dev.queiroz.relay.delivery.application;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
@Service
@ConditionalOnProperty(name="relay.worker.enabled", havingValue="true")
public class DeliveryWorker {
 private final DeliveryClaimService claims; private final HttpDeliveryDispatcher dispatcher; private final RetryPolicy retry; private final Clock clock; private final AtomicBoolean accepting=new AtomicBoolean(true);
 public DeliveryWorker(DeliveryClaimService claims,HttpDeliveryDispatcher dispatcher,RetryPolicy retry,Clock clock){this.claims=claims;this.dispatcher=dispatcher;this.retry=retry;this.clock=clock;}
 @Scheduled(fixedDelayString="${relay.worker.poll-interval:PT1S}") void poll(){if(!accepting.get())return;for(var d:claims.claimDue(25,Duration.ofSeconds(30))){try{int status=dispatcher.dispatch(d);var next=retry.afterResponse(d.attemptNumber(),status,clock.instant());claims.finalize(d.deliveryId(),d.leaseToken(),next.state(),next.nextAttemptAt());}catch(Exception e){var next=retry.afterTransportFailure(d.attemptNumber(),clock.instant());claims.finalize(d.deliveryId(),d.leaseToken(),next.state(),next.nextAttemptAt());}}}
 @PreDestroy void stop(){accepting.set(false);}
}
