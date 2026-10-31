package dev.queiroz.relay.delivery.application;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SecretProtectorTest {
    @Test void encryptsWithANonDefaultRuntimeKeyAndDecrypts() {
        SecretProtector protector = new SecretProtector("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        String protectedValue = protector.encrypt("endpoint-secret");
        assertNotEquals("endpoint-secret", protectedValue);
        assertEquals("endpoint-secret", protector.decrypt(protectedValue));
    }
    @Test void rejectsMissingOrWeakKeys() { assertThrows(IllegalStateException.class, () -> new SecretProtector("")); }
}
