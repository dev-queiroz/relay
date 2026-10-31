# M4 operations runbook

Run `scripts/migrate-backup-restore.sh` with `DATABASE_URL` before migrations; restore only into an isolated database and verify Flyway history plus readiness. Versioned migrations are immutable and roll forward; PITR requires managed PostgreSQL WAL archiving.

The worker uses bounded batches, leases and graceful SIGTERM shutdown. Scale only with shared PostgreSQL and monitor queued, leased, backlog-age and failure metrics.

Run `k6 run -e RELAY_URL=... -e RELAY_API_KEY=... benchmarks/ingestion.js`; retain latency, DB CPU and pool evidence. Install `ops/alerts.yml` and investigate stale queues or failures through correlation IDs.

Build with `docker build -t relay:verify .`; the image runs as non-root. Run `scripts/release-check.sh` before immutable tags. Public ingress remains blocked until the M3 security gate passes.
