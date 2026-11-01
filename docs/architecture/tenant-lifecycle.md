# Tenant lifecycle

## Provisioning

An internal operator creates a tenant identity and records its accountable owner before issuing an API key. Keys are tenant-bound, stored hashed, may expire, and are delivered through an approved secret channel. There is no public tenant or key-issuance endpoint.

## Ownership and audit evidence

Record the tenant identifier, owner reference, operator reference, action, timestamp and reason in an immutable operational audit system. Do not record raw API keys, signing secrets, event payloads or destination URLs. Key issuance, revocation, endpoint disablement and lifecycle state changes require audit records.

## Deactivation

Revoke active API keys and disable endpoints to stop new ingestion and scheduling. Existing delivery records remain available according to the approved retention policy; deactivation is not deletion and does not claim cancellation of already queued work.

## Deletion and privacy review

Hard deletion is intentionally deferred. A retention schedule, legal basis, privacy obligations, backup/PITR treatment and an auditable erasure workflow must be approved before implementation. The future workflow must be tenant-scoped, resumable, idempotent and verified against events, deliveries, attempts, idempotency records and encrypted signing keys.

## Access boundaries

Public API keys never grant cross-tenant access. Internal support access requires least privilege and separate audit controls; it is not supplied by the public API.
