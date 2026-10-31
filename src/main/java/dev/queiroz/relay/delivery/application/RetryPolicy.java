package dev.queiroz.relay.delivery.application;
import org.springframework.stereotype.Component;
import java.time.*;
@Component public class RetryPolicy {
 private static final Duration[] BACKOFF={Duration.ofSeconds(30),Duration.ofMinutes(2),Duration.ofMinutes(10),Duration.ofHours(1)};
 public Decision afterResponse(int attempts,int status,Instant now){ boolean retryable=status==408||status==429||status>=500; return decide(attempts,retryable,now); }
 public Decision afterTransportFailure(int attempts,Instant now){return decide(attempts,true,now);}
 private Decision decide(int attempts,boolean retryable,Instant now){if(!retryable||attempts>=5)return new Decision("EXHAUSTED",now);return new Decision("RETRY_WAIT",now.plus(BACKOFF[attempts-1]));}
 public record Decision(String state,Instant nextAttemptAt){}
}
