package dev.queiroz.relay.delivery.application;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;

@Component
public class WebhookSignature {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final Clock clock;

    public WebhookSignature(Clock clock) { this.clock = clock; }

    public String timestamp() { return Long.toString(clock.instant().getEpochSecond()); }

    public String sign(String secret, String timestamp, byte[] body) {
        if (secret == null || secret.isBlank() || timestamp == null || timestamp.isBlank()) throw new IllegalArgumentException("secret and timestamp are required");
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return "v1=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception exception) { throw new IllegalStateException("unable to sign webhook", exception); }
    }

    public boolean verify(String secret, String timestamp, String signature, byte[] body, Duration maxAge) {
        try {
            long received = Long.parseLong(timestamp);
            if (maxAge.isNegative() || Math.abs(clock.instant().getEpochSecond() - received) > maxAge.toSeconds()) return false;
            return MessageDigest.isEqual(sign(secret, timestamp, body).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
        } catch (RuntimeException exception) { return false; }
    }
}
