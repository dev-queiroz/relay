# Domain Model — Initial Scope

## Event
Immutable accepted customer fact. Fields: UUID ID, tenant ID, type, JSON payload, createdAt UTC, status (initially ACCEPTED). Its identity is server-generated.

## IdempotencyRecord
Tracks a request within one tenant: tenantId, key, requestFingerprint, eventId, createdAt. Event and record are persisted within one transaction. Unique (tenantId, key).

## Future concepts (not yet implemented)
- **Endpoint:** a tenant-owned HTTPS destination.
- **Delivery:** an event destined for one endpoint; separate from Event.
- **Attempt:** a single HTTP call to fulfill a delivery.
- **Tenant:** security/data isolation boundary.

## Invariants
- Events may exist without deliveries; acceptance is not delivery.
- A delivery is not an attempt; one delivery may have many attempts.
- Retries of identical ingestion do not create a second event.
- One tenant cannot reuse another tenant's idempotency namespace or read its events.
- All persisted timestamps are UTC.

## Planned state machine
Event: ACCEPTED only during ingestion. Future delivery state belongs to Delivery, not Event. No fake DELIVERED event state in M1.
