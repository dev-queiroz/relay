package dev.queiroz.relay.delivery.application;

import dev.queiroz.relay.delivery.domain.Endpoint;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.Instant;
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
    public List<Endpoint> page(TenantId tenant, Instant createdBefore, UUID idBefore, int limit) {
        String sql = """
                SELECT id, tenant_id, name, destination_url, enabled, created_at
                FROM endpoints
                WHERE tenant_id = ?
                  AND (CAST(? AS timestamptz) IS NULL OR created_at < ? OR (created_at = ? AND id < ?))
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """;
        Timestamp before = createdBefore == null ? null : Timestamp.from(createdBefore);
        return jdbc.query(sql, this::mapEndpoint, tenant.value(), before, before, before, idBefore, limit);
    }
    public Optional<Endpoint> update(TenantId tenant, UUID id, String name, URI destinationUrl, Boolean enabled) {
        String sql = """
                UPDATE endpoints
                SET name = COALESCE(?, name),
                    destination_url = COALESCE(?, destination_url),
                    enabled = COALESCE(?, enabled)
                WHERE tenant_id = ? AND id = ?
                RETURNING id, tenant_id, name, destination_url, enabled, created_at
                """;
        return jdbc.query(sql, this::mapEndpoint, name,
                destinationUrl == null ? null : destinationUrl.toString(), enabled, tenant.value(), id)
                .stream().findFirst();
    }
    public boolean setEnabled(TenantId tenant, UUID id, boolean enabled) { return jdbc.update("UPDATE endpoints SET enabled = ? WHERE tenant_id = ? AND id = ?", enabled, tenant.value(), id) == 1; }
    private Endpoint mapEndpoint(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Endpoint(rs.getObject("id", UUID.class), new TenantId(rs.getString("tenant_id")), rs.getString("name"),
                URI.create(rs.getString("destination_url")), rs.getBoolean("enabled"), rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
