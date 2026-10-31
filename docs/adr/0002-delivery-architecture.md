# ADR-0002 — Durable Webhook Delivery Architecture

- Status: Accepted for M2 local development
- Date: 2026-10-31
- Owner: Tech Lead

## Context
M1 durably accepts events in PostgreSQL. Acceptance does not imply HTTP delivery. M2 must deliver to tenant-owned destinations while surviving process crashes, HTTP timeouts, duplicates, and worker restarts.

## Decision
Continue the Java modular monolith with PostgreSQL as the system of record. Separate Endpoint, Delivery and Attempt concepts. Do not add Kafka, Redis, or a second service in M2.

The delivery worker uses a PostgreSQL-backed queue: select due deliveries with row-level locking (`FOR UPDATE SKIP LOCKED`) and a persisted lease token/expiration. Claim transactions are short; never hold a database transaction open during an HTTP call. Finalize only if the current lease token still matches. Expired leases become eligible for recovery.

**Delivery guarantee:** at-least-once delivery attempts, not exactly-once side effects. A remote endpoint may process an HTTP request before the worker loses the response. Retrying can cause duplicates. Send a stable delivery ID to support downstream deduplication.

**Ordering:** no ordering guarantee between deliveries or endpoints in M2.

**Scheduling:** create Delivery records for active endpoint subscriptions in the same transaction as newly accepted Event; exact duplicate ingestions must not schedule duplicates. Existing M1 events are not automatically backfilled. Events without subscribed endpoints remain accepted without deliveries.

## Security boundary
M2 is local/private development only. A configurable allowlist of safe fixture destinations is required before outbound network calls are enabled. Never expose an arbitrary URL-fetching API or deploy the dispatcher publicly. M3 must implement robust SSRF resistance (DNS and redirect validation, private-address blocking, egress policy), tenant authentication, HMAC signatures and rate limiting before public release.

## Consequences
DB downtime stalls scheduling and delivery. Leases provide recovery but cannot prevent all duplicated attempts. Monitoring, retention and scalability remain future work. Retain attempt records for the pilot; policy to be decided before production.

## Revisit triggers
Real workload data, operational SLAs, multi-worker contention, public network exposure, tenant self-service or delivery throughput limits.
