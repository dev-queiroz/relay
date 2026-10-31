# M2 delivery demonstration

Run the full quality gate with Docker available:

```sh
./mvnw --batch-mode verify
```

The Testcontainers suite exercises PostgreSQL migrations, tenant-safe endpoint registration, atomic delivery scheduling, idempotent ingestion, due-work claiming, lease fencing, expired-lease reclaim, and deterministic retry policy.

## Reproducible scenario

1. Start PostgreSQL with `docker compose up -d` and configure `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
2. Register an enabled local fixture endpoint whose origin appears in `relay.delivery.local-target-allowlist`.
3. Ingest one event with an `Idempotency-Key`; exactly one pending delivery is scheduled.
4. Claim the delivery, dispatch it, then finalize with the lease token.
5. Repeat ingestion with the same key and body; no delivery is added.
6. For a retryable failure, finalize as `RETRY_WAIT` at the deterministic backoff time. Expired leases can be reclaimed; a fifth expired lease becomes `EXHAUSTED`.

## Guarantee boundary

M2 is at-least-once. A receiver can process a request before Relay loses the response or worker lease. A later retry can therefore duplicate a downstream side effect. Receivers must deduplicate by `X-Relay-Delivery-Id`. This is local-only development behavior; M3 security controls are required before public networking.
