package dev.queiroz.relay.api.pagination;

import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/** HMAC-protected, tenant- and route-bound cursors shared by public list routes. */
@Component
public class CursorCodec {
    private static final Duration LIFETIME = Duration.ofHours(24);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final byte[] key;
    private final Clock clock;

    public CursorCodec(@Value("${relay.pagination.cursor-hmac-key}") String encodedKey, Clock clock) {
        try {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length < 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("relay.pagination.cursor-hmac-key must be a base64-encoded key of at least 256 bits", exception);
        }
        this.clock = clock;
    }

    public String encode(String route, TenantId tenant, KeysetCursor cursor) {
        String payload = String.join("|", "v1", route, ENCODER.encodeToString(tenant.value().getBytes(StandardCharsets.UTF_8)),
                Long.toString(cursor.createdAt().getEpochSecond()), Integer.toString(cursor.createdAt().getNano()),
                cursor.id().toString(), Long.toString(clock.instant().plus(LIFETIME).getEpochSecond()));
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + ENCODER.encodeToString(sign(payload));
    }

    public KeysetCursor decode(String token, String route, TenantId tenant) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            String payload = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(sign(payload), DECODER.decode(parts[1]))) throw new IllegalArgumentException();
            String[] fields = payload.split("\\|", -1);
            if (fields.length != 7 || !"v1".equals(fields[0]) || !route.equals(fields[1])) throw new IllegalArgumentException();
            String boundTenant = new String(DECODER.decode(fields[2]), StandardCharsets.UTF_8);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(fields[6]));
            if (!tenant.value().equals(boundTenant) || !expiresAt.isAfter(clock.instant())) throw new IllegalArgumentException();
            return new KeysetCursor(Instant.ofEpochSecond(Long.parseLong(fields[3]), Long.parseLong(fields[4])), UUID.fromString(fields[5]));
        } catch (RuntimeException exception) {
            throw new InvalidCursorException();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("unable to sign cursor", exception);
        }
    }
}
