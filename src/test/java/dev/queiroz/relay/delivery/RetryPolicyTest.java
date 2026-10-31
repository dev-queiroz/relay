package dev.queiroz.relay.delivery;
import dev.queiroz.relay.delivery.application.RetryPolicy; import org.junit.jupiter.api.Test; import java.time.*; import static org.junit.jupiter.api.Assertions.*;
class RetryPolicyTest { RetryPolicy p=new RetryPolicy(); Instant now=Instant.parse("2026-01-01T00:00:00Z");
 @Test void usesDeterministicBackoff(){assertEquals(now.plusSeconds(30),p.afterResponse(1,500,now).nextAttemptAt());assertEquals(now.plus(Duration.ofHours(1)),p.afterResponse(4,429,now).nextAttemptAt());}
 @Test void exhaustsPermanentAndFifthFailures(){assertEquals("EXHAUSTED",p.afterResponse(1,400,now).state());assertEquals("EXHAUSTED",p.afterTransportFailure(5,now).state());}
}
