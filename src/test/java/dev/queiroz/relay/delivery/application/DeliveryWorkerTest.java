package dev.queiroz.relay.delivery.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryWorkerTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void finalizesSuccessfulHttpResponseAsSucceeded() throws Exception {
        assertHttpResponseFinalization(204, 1, "SUCCEEDED", now);
    }

    @Test
    void exhaustsPermanentHttpResponseWithoutRetry() throws Exception {
        assertHttpResponseFinalization(400, 1, "EXHAUSTED", now);
    }

    @Test
    void retriesRetryableHttpResponseUntilTheAttemptBudgetIsSpent() throws Exception {
        assertHttpResponseFinalization(503, 1, "RETRY_WAIT", now.plusSeconds(30));
        assertHttpResponseFinalization(503, 5, "EXHAUSTED", now);
    }

    @Test
    void retriesTransportFailuresUntilTheAttemptBudgetIsSpent() throws Exception {
        DeliveryClaimService claims = mock(DeliveryClaimService.class);
        HttpDeliveryDispatcher dispatcher = mock(HttpDeliveryDispatcher.class);
        ClaimedDelivery delivery = delivery(1);
        when(claims.claimDue(25, Duration.ofSeconds(30))).thenReturn(List.of(delivery));
        doThrow(new IOException("synthetic transport failure")).when(dispatcher).dispatch(delivery);

        worker(claims, dispatcher).poll();

        verify(claims).finalize(delivery.deliveryId(), delivery.leaseToken(), "RETRY_WAIT", now.plusSeconds(30));
    }

    private void assertHttpResponseFinalization(int status, int attempt, String state, Instant nextAttemptAt) throws Exception {
        DeliveryClaimService claims = mock(DeliveryClaimService.class);
        HttpDeliveryDispatcher dispatcher = mock(HttpDeliveryDispatcher.class);
        ClaimedDelivery delivery = delivery(attempt);
        when(claims.claimDue(25, Duration.ofSeconds(30))).thenReturn(List.of(delivery));
        when(dispatcher.dispatch(delivery)).thenReturn(status);

        worker(claims, dispatcher).poll();

        verify(claims).finalize(delivery.deliveryId(), delivery.leaseToken(), state, nextAttemptAt);
    }

    private DeliveryWorker worker(DeliveryClaimService claims, HttpDeliveryDispatcher dispatcher) {
        return new DeliveryWorker(claims, dispatcher, new RetryPolicy(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private ClaimedDelivery delivery(int attempt) {
        return new ClaimedDelivery(UUID.randomUUID(), "tenant", UUID.randomUUID(), UUID.randomUUID(),
                "https://receiver.example.test/webhook", attempt, UUID.randomUUID());
    }
}
