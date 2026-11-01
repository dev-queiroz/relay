package dev.queiroz.relay.delivery.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import dev.queiroz.relay.operations.OperationalLog;
import dev.queiroz.relay.operations.DeliveryMetrics;
import dev.queiroz.relay.operations.RelayTracing;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class HttpDeliveryDispatcherTest {
    @Test
    void refusesAReboundOrPrivateDestinationBeforeReadingTenantData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")});
        EndpointSigningKeyStore keys = mock(EndpointSigningKeyStore.class);
        DeliveryMetrics metrics = mock(DeliveryMetrics.class);
        HttpDeliveryDispatcher dispatcher = new HttpDeliveryDispatcher(jdbc, policy, keys, new WebhookSignature(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), mock(OperationalLog.class), metrics, new RelayTracing());
        ClaimedDelivery delivery = new ClaimedDelivery(UUID.randomUUID(), "tenant-a", UUID.randomUUID(), UUID.randomUUID(), "https://rebind.example.test/hook", 1, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(delivery));
        verifyNoInteractions(jdbc, keys);
        org.mockito.Mockito.verify(metrics).attemptStarted();
        org.mockito.Mockito.verify(metrics).deliveryFailed();
    }

    @Test
    void persistsSucceededOutcomeForHttp204() throws Exception {
        assertPersistedHttpOutcome(204, "SUCCEEDED");
    }

    @Test
    void persistsPermanentOutcomeForRedirectsAndPermanent4xxResponses() throws Exception {
        for (int status : new int[]{302, 400, 404}) {
            assertPersistedHttpOutcome(status, "PERMANENT_FAILURE");
        }
    }

    @Test
    void persistsRetryableOutcomeForTimeoutRateLimitAndServerErrors() throws Exception {
        for (int status : new int[]{408, 429, 500, 503}) {
            assertPersistedHttpOutcome(status, "RETRYABLE_FAILURE");
        }
    }

    @SuppressWarnings("unchecked")
    private void assertPersistedHttpOutcome(int status, String expectedOutcome) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        EndpointSigningKeyStore keys = mock(EndpointSigningKeyStore.class);
        HttpClient client = mock(HttpClient.class);
        HttpResponse<Void> response = mock(HttpResponse.class);
        DeliveryMetrics metrics = mock(DeliveryMetrics.class);
        UUID eventId = UUID.randomUUID();
        UUID endpointId = UUID.randomUUID();
        ClaimedDelivery delivery = new ClaimedDelivery(UUID.randomUUID(), "tenant-a", eventId, endpointId,
                "https://receiver.example.test/webhook", 1, UUID.randomUUID());
        when(jdbc.queryForMap(anyString(), eq("tenant-a"), eq(eventId)))
                .thenReturn(Map.of("id", eventId, "event_type", "test", "payload", "{}", "created_at", Instant.EPOCH));
        when(keys.activeSecret("tenant-a", endpointId)).thenReturn(Optional.of("synthetic-signing-secret"));
        when(response.statusCode()).thenReturn(status);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
        HttpDeliveryDispatcher dispatcher = new HttpDeliveryDispatcher(jdbc, policy, keys,
                new WebhookSignature(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)), mock(OperationalLog.class), metrics,
                new RelayTracing(), client);

        dispatcher.dispatch(delivery);

        verify(jdbc).update(eq("UPDATE delivery_attempts SET finished_at=CURRENT_TIMESTAMP,outcome=?,response_status=? WHERE id=?"),
                eq(expectedOutcome), eq(status), any(UUID.class));
        if ("SUCCEEDED".equals(expectedOutcome)) {
            verify(metrics).deliverySucceeded();
        } else {
            verify(metrics).deliveryFailed();
        }
    }
}
