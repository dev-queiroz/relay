package dev.queiroz.relay.delivery.domain;

import dev.queiroz.relay.delivery.application.LocalTargetPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalTargetPolicyTest {
    private final LocalTargetPolicy policy = new LocalTargetPolicy("http://localhost:8081,https://fixtures.test");
    @Test void acceptsAllowlistedTargets() { assertDoesNotThrow(() -> policy.validate("http://localhost:8081/hooks/a")); assertDoesNotThrow(() -> policy.validate("https://fixtures.test/hooks")); }
    @Test void rejectsUnsafeOrNonAllowlistedTargets() { assertThrows(IllegalArgumentException.class, () -> policy.validate("ftp://localhost:8081/a")); assertThrows(IllegalArgumentException.class, () -> policy.validate("http://user:pass@localhost:8081/a")); assertThrows(IllegalArgumentException.class, () -> policy.validate("http://example.com/a")); }
}
