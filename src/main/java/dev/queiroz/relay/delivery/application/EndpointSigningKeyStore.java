package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EndpointSigningKeyStore {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecretProtector protector;

    public EndpointSigningKeyStore(JdbcTemplate jdbc, Clock clock, SecretProtector protector) { this.jdbc = jdbc; this.clock = clock; this.protector = protector; }

    public String rotate(TenantId tenant, UUID endpointId) {
        if (jdbc.queryForObject("SELECT count(*) FROM endpoints WHERE tenant_id=? AND id=?", Integer.class, tenant.value(), endpointId) != 1) {
            throw new IllegalArgumentException("endpoint does not belong to tenant");
        }
        jdbc.update("UPDATE endpoint_signing_keys SET retired_at=? WHERE tenant_id=? AND endpoint_id=? AND retired_at IS NULL", java.sql.Timestamp.from(clock.instant()), tenant.value(), endpointId);
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO endpoint_signing_keys (id,tenant_id,endpoint_id,secret) VALUES (?,?,?,?)", UUID.randomUUID(), tenant.value(), endpointId, protector.encrypt(secret));
        return secret;
    }

    public Optional<String> activeSecret(String tenantId, UUID endpointId) {
        return jdbc.query("SELECT secret FROM endpoint_signing_keys WHERE tenant_id=? AND endpoint_id=? AND retired_at IS NULL", (rs, row) -> protector.decrypt(rs.getString(1)), tenantId, endpointId).stream().findFirst();
    }
}
