package dev.queiroz.relay.delivery.application;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.InetAddress;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class HttpDeliveryDispatcherTest {
    @Test
    void refusesAReboundOrPrivateDestinationBeforeReadingTenantData() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName("169.254.169.254")});
        HttpDeliveryDispatcher dispatcher = new HttpDeliveryDispatcher(jdbc, policy);
        ClaimedDelivery delivery = new ClaimedDelivery(UUID.randomUUID(), "tenant-a", UUID.randomUUID(), "https://rebind.example.test/hook", 1, UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch(delivery));
        verifyNoInteractions(jdbc);
    }
}
