package dev.queiroz.relay.ingestion.application;

import dev.queiroz.relay.ingestion.domain.TenantId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Database-backed fixed windows provide a single decision across application replicas. */
@Service
public class TenantRateLimitService {
    private final JdbcTemplate jdbc;
    private final int requestsPerMinute;
    private final int eventsPerDay;

    public TenantRateLimitService(JdbcTemplate jdbc,
                                  @Value("${relay.limits.ingestion-requests-per-minute:60}") int requestsPerMinute,
                                  @Value("${relay.limits.events-per-tenant-per-day:10000}") int eventsPerDay) {
        if (requestsPerMinute < 1 || eventsPerDay < 1) throw new IllegalArgumentException("limits must be positive");
        this.jdbc = jdbc; this.requestsPerMinute = requestsPerMinute; this.eventsPerDay = eventsPerDay;
    }

    public boolean allowRequest(TenantId tenant) {
        return !jdbc.query("""
                INSERT INTO tenant_request_windows (tenant_id, window_started_at, request_count)
                VALUES (?, date_trunc('minute', CURRENT_TIMESTAMP), 1)
                ON CONFLICT (tenant_id, window_started_at) DO UPDATE SET request_count = tenant_request_windows.request_count + 1
                WHERE tenant_request_windows.request_count < ?
                RETURNING request_count
                """, (rs, row) -> rs.getInt(1), tenant.value(), requestsPerMinute).isEmpty();
    }

    /** Called in the event-acceptance transaction, so rejected duplicates do not consume quota. */
    public boolean reserveDailyEvent(TenantId tenant) {
        return !jdbc.query("""
                INSERT INTO tenant_event_quota_days (tenant_id, quota_day, event_count)
                VALUES (?, CURRENT_DATE, 1)
                ON CONFLICT (tenant_id, quota_day) DO UPDATE SET event_count = tenant_event_quota_days.event_count + 1
                WHERE tenant_event_quota_days.event_count < ?
                RETURNING event_count
                """, (rs, row) -> rs.getInt(1), tenant.value(), eventsPerDay).isEmpty();
    }
}
