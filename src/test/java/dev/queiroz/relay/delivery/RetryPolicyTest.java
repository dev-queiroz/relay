package dev.queiroz.relay.delivery;

import dev.queiroz.relay.delivery.application.RetryPolicy;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RetryPolicyTest {
    private final RetryPolicy policy = new RetryPolicy();
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void succeedsForEverySuccessfulHttpResponse() {
        for (int status : new int[]{200, 201, 202, 204}) {
            assertEquals("SUCCEEDED", policy.afterResponse(1, status, now).state(), "status " + status);
        }
    }

    @Test
    void usesDeterministicBackoffForRetryableResponses() {
        assertEquals(now.plusSeconds(30), policy.afterResponse(1, 408, now).nextAttemptAt());
        assertEquals(now.plusSeconds(30), policy.afterResponse(1, 429, now).nextAttemptAt());
        assertEquals(now.plus(Duration.ofHours(1)), policy.afterResponse(4, 500, now).nextAttemptAt());
        assertEquals(now.plusSeconds(30), policy.afterResponse(1, 503, now).nextAttemptAt());
    }

    @Test
    void exhaustsPermanentAndFifthFailures() {
        assertEquals("EXHAUSTED", policy.afterResponse(1, 301, now).state());
        assertEquals("EXHAUSTED", policy.afterResponse(1, 400, now).state());
        assertEquals("EXHAUSTED", policy.afterResponse(5, 503, now).state());
        assertEquals("EXHAUSTED", policy.afterTransportFailure(5, now).state());
    }
}
