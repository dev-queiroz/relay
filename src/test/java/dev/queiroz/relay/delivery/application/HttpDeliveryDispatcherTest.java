package dev.queiroz.relay.delivery.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HttpDeliveryDispatcherTest {
    @Test
    void refusesAReboundOrPrivateDestinationBeforeReadingTenantData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")});
        EndpointSigningKeyStore keys = mock(EndpointSigningKeyStore.class);
        HttpDeliveryDispatcher dispatcher = new HttpDeliveryDispatcher(jdbc, policy, keys, new WebhookSignature(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));
        ClaimedDelivery delivery = new ClaimedDelivery(UUID.randomUUID(), "tenant-a", UUID.randomUUID(), UUID.randomUUID(), "https://rebind.example.test/hook", 1, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(delivery));
        verifyNoInteractions(jdbc, keys);
    }
}
