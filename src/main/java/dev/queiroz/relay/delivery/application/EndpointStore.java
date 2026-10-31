package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EndpointStore {
    private final JdbcTemplate jdbc;
    public EndpointStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Endpoint create(Endpoint endpoint) {
        jdbc.update("INSERT INTO endpoints (id, tenant_id, name, destination_url, enabled, created_at) VALUES (?, ?, ?, ?, ?, ?)", endpoint.id(), endpoint.tenantId().value(), endpoint.name(), endpoint.destinationUrl().toString(), endpoint.enabled(), Timestamp.from(endpoint.createdAt()));
        return endpoint;
    }
    public Optional<Endpoint> find(TenantId tenant, UUID id) {
        List<Endpoint> results = jdbc.query("SELECT id, tenant_id, name, destination_url, enabled, created_at FROM endpoints WHERE tenant_id = ? AND id = ?", (rs, row) -> new Endpoint(rs.getObject("id", UUID.class), new TenantId(rs.getString("tenant_id")), rs.getString("name"), URI.create(rs.getString("destination_url")), rs.getBoolean("enabled"), rs.getObject("created_at", OffsetDateTime.class).toInstant()), tenant.value(), id);
        return results.stream().findFirst();
    }
    public List<Endpoint> enabledFor(TenantId tenant) {
        return jdbc.query("SELECT id, tenant_id, name, destination_url, enabled, created_at FROM endpoints WHERE tenant_id = ? AND enabled = true", (rs, row) -> new Endpoint(rs.getObject("id", UUID.class), new TenantId(rs.getString("tenant_id")), rs.getString("name"), URI.create(rs.getString("destination_url")), true, rs.getObject("created_at", OffsetDateTime.class).toInstant()), tenant.value());
    }
    public boolean setEnabled(TenantId tenant, UUID id, boolean enabled) { return jdbc.update("UPDATE endpoints SET enabled = ? WHERE tenant_id = ? AND id = ?", enabled, tenant.value(), id) == 1; }
}
