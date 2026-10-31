package dev.queiroz.relay.delivery.domain;

import dev.queiroz.relay.delivery.application.LocalTargetPolicy;
import org.junit.jupiter.api.Test;
import java.net.InetAddress;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalTargetPolicyTest {
    @Test void acceptsHttpsDestinationResolvingOnlyToPublicAddresses() {
        LocalTargetPolicy policy = policy("8.8.8.8");
        assertDoesNotThrow(() -> policy.validate("https://hooks.example.test/a"));
    }

    @Test void rejectsNonHttpsCredentialsAndPrivateAddressRanges() {
        assertThrows(IllegalArgumentException.class, () -> policy("8.8.8.8").validate("http://hooks.example.test/a"));
        assertThrows(IllegalArgumentException.class, () -> policy("8.8.8.8").validate("https://user:pass@hooks.example.test/a"));
        assertThrows(IllegalArgumentException.class, () -> policy("127.0.0.1").validate("https://hooks.example.test/a"));
        assertThrows(IllegalArgumentException.class, () -> policy("169.254.169.254").validate("https://hooks.example.test/a"));
        assertThrows(IllegalArgumentException.class, () -> policy("fc00::1").validate("https://hooks.example.test/a"));
    }

    @Test void rejectsAHostnameWhenAnyDnsAnswerIsNotPublic() {
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8"), InetAddress.getByName("10.0.0.7")});
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://rebind.example.test/a"));
    }

    @Test void detectsAReboundPrivateAnswerOnTheNextResolution() {
        java.util.concurrent.atomic.AtomicInteger resolutions = new java.util.concurrent.atomic.AtomicInteger();
        LocalTargetPolicy policy = new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName(resolutions.getAndIncrement() == 0 ? "8.8.8.8" : "10.0.0.7")});
        assertDoesNotThrow(() -> policy.validate("https://rebind.example.test/a"));
        assertThrows(IllegalArgumentException.class, () -> policy.validate("https://rebind.example.test/a"));
    }

    private LocalTargetPolicy policy(String address) {
        return new LocalTargetPolicy(host -> new InetAddress[]{InetAddress.getByName(address)});
    }
}
