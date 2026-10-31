# Webhook Delivery Contract — M2 v0.1

Status: Accepted for local-only M2 implementation. Owner: Tech Lead.

## Entities and persistence
- **Endpoint**: UUID id, tenantId, name, destinationUrl, enabled, createdAt UTC. Endpoint belongs to exactly one tenant.
- **Delivery**: UUID id, tenantId, eventId, endpointId, state, attemptsCount, nextAttemptAt, leaseToken (nullable UUID), leaseExpiresAt (nullable), createdAt, updatedAt. Unique (eventId, endpointId). FK relationships MUST prevent tenant crossing.
- **Attempt**: UUID id, deliveryId, sequenceNumber, startedAt, finishedAt nullable, outcome, responseStatus nullable, sanitizedErrorCode nullable. Unique (deliveryId, sequenceNumber). No secret headers or payload copies in errors.

## State machine
- `PENDING`: ready when nextAttemptAt <= now.
- `IN_FLIGHT`: worker currently owns a persisted lease.
- `SUCCEEDED`: an HTTP 2xx response was observed.
- `RETRY_WAIT`: retryable error and future nextAttemptAt.
- `EXHAUSTED`: retry budget elapsed or permanent failure.

Transitions: PENDING/RETRY_WAIT -> IN_FLIGHT -> SUCCEEDED/RETRY_WAIT/EXHAUSTED. Expired IN_FLIGHT leases are recoverable as due work; do not create separate permanent LOST state.

## Endpoint registration (later M2 ticket)
Endpoint registration is local-only initially: dev-only fixed tenant and explicit allowlisted fixture destinations, no public tenant-supplied arbitrary URL submission. Enforce HTTPS for production; local mock server may use loopback HTTP only when explicitly enabled by development profile.

## Scheduling contract
When a new event is durably accepted, insert one Delivery per enabled endpoint of same tenant in the same database transaction. No retroactive delivery to endpoints added after event acceptance. A duplicate idempotent request creates zero additional deliveries. Existing M1 data is not backfilled automatically.

## Worker and HTTP attempt
- Claim up to a bounded batch of due deliveries using PostgreSQL locking with SKIP LOCKED, a lease token, lease expiration and a short transaction.
- Persist an Attempt before contacting destination; increment attemptsCount atomically. Do not keep DB transaction open across HTTP.
- Request: POST application/json with stable `X-Relay-Delivery-Id`, `X-Relay-Event-Id` and `X-Relay-Attempt`. Payload envelope: `{ "id": "<event UUID>", "type": "<event type>", "createdAt": "<ISO-8601>", "payload": {...} }`.
- Default connect timeout: 2 s; total request timeout: 5 s. Do not follow redirects.
- 2xx => SUCCEEDED. 408, 429, 5xx, transport errors and timeout => retryable. Other 3xx/4xx => EXHAUSTED (no redirect).
- A late result with an expired/replaced lease token must not overwrite newer state.
- Endpoint destination modifications do not rewrite attempted historical deliveries; record an immutable destination snapshot on Delivery before HTTP work begins (future endpoint story).

## Retry policy
At most 5 total attempts (first + 4 retries), unless explicitly changed by ADR.
Backoff after failure (nominal, without jitter for deterministic initial tests): 30 s, 2 min, 10 min, 1 hour. On final failure, EXHAUSTED.
Acknowledge downstream duplicate processing risk; destination should deduplicate by X-Relay-Delivery-Id.

## Non-goals
No exactly-once guarantee, customer-facing endpoint management API, status-query API, UI, delivery ordering, public internet dispatch, arbitrary URL fetching, billing or production security claim.

## Validation gates
Migration/constraints tests; transactional schedule test; concurrent claims; crash/lease recovery; mock HTTP server success, timeout, 429, 500, 400 and redirect; deterministic backoff; isolation between tenants. Docker-based integration tests required for PostgreSQL-specific claims.
