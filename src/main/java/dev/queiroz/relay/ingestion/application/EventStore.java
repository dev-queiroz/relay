package dev.queiroz.relay.ingestion.application;

import dev.queiroz.relay.ingestion.domain.Event;
import dev.queiroz.relay.ingestion.domain.IdempotencyRecord;
import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class EventStore {

    private final JdbcTemplate jdbcTemplate;

    public EventStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Event event) {
        jdbcTemplate.update(
                """
                INSERT INTO events (id, tenant_id, event_type, payload, status, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?)
                """,
                event.id().value(),
                event.tenantId().value(),
                event.type().value(),
                event.payload().value().toString(),
                event.status().name(),
                Timestamp.from(event.createdAt())
        );
    }

    public void insert(IdempotencyRecord record) {
        jdbcTemplate.update(
                """
                INSERT INTO idempotency_records
                    (id, tenant_id, idempotency_key, request_fingerprint, event_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                record.id().value(),
                record.tenantId().value(),
                record.key(),
                record.requestFingerprint(),
                record.eventId().value(),
                Timestamp.from(record.createdAt())
        );
    }

    public Optional<IdempotencyRecord> findIdempotencyRecord(TenantId tenantId, String key) {
        List<IdempotencyRecord> records = jdbcTemplate.query(
                """
                SELECT id, tenant_id, idempotency_key, request_fingerprint, event_id, created_at
                FROM idempotency_records
                WHERE tenant_id = ? AND idempotency_key = ?
                """,
                (resultSet, rowNum) -> new IdempotencyRecord(
                        new dev.queiroz.relay.ingestion.domain.IdempotencyRecordId(
                                resultSet.getObject("id", UUID.class)
                        ),
                        new TenantId(resultSet.getString("tenant_id")),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("request_fingerprint"),
                        new dev.queiroz.relay.ingestion.domain.EventId(
                                resultSet.getObject("event_id", UUID.class)
                        ),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
                ),
                tenantId.value(),
                key
        );
        return records.stream().findFirst();
    }
}
