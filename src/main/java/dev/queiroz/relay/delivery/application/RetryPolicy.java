package dev.queiroz.relay.delivery.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RetryPolicy {
    private static final Duration[] BACKOFF = {
            Duration.ofSeconds(30), Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1)
    };

    public Decision afterResponse(int attempts, int status, Instant now) {
        return afterOutcome(attempts, DeliveryOutcome.forHttpStatus(status), now);
    }

    public Decision afterTransportFailure(int attempts, Instant now) {
        return afterOutcome(attempts, DeliveryOutcome.RETRYABLE_FAILURE, now);
    }

    private Decision afterOutcome(int attempts, DeliveryOutcome outcome, Instant now) {
        if (outcome == DeliveryOutcome.SUCCEEDED) {
            return new Decision("SUCCEEDED", now);
        }
        if (outcome == DeliveryOutcome.PERMANENT_FAILURE || attempts >= 5) {
            return new Decision("EXHAUSTED", now);
        }
        return new Decision("RETRY_WAIT", now.plus(BACKOFF[attempts - 1]));
    }

    public record Decision(String state, Instant nextAttemptAt) {
    }
}
