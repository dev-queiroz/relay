# Event Ingestion Contract — v0.1

Status: **Approved for first implementation slice**. Owner: Tech Lead. Contract changes require a reviewed PR.

## Scope
The first slice provides `POST /v1/events` for one authenticated tenant and durable event ingestion. Actual webhook delivery is **out of scope**. Public authentication is introduced in a later ticket; during local development use an explicit fixed development tenant, never accept a tenant identifier from untrusted request bodies.

## Request
`POST /v1/events`
- `Content-Type: application/json`
- `Idempotency-Key`: required UUID string, length <= 64
- JSON object:
```json
{"type":"payment.confirmed","payload":{"paymentId":"pay_123"}}
```
- `type`: required nonblank string, length 1–128, ASCII letters, digits, dots, underscores and hyphens.
- `payload`: required JSON object. Maximum request body: 64 KiB including metadata. Reject oversized requests with 413.
- Any server-generated event ID uses UUID; client does not choose database identity.
- `createdAt` is server-generated UTC timestamp.

## Success
- `202 Accepted` **only after a database transaction has committed** the event and the idempotency record atomically. Event can later fail delivery.
- Response is identical for the original request and exact retries within the retention window:
```json
{"eventId":"<UUID>","status":"accepted"}
```
- Include `Location: /v1/events/{eventId}`; status endpoint will be implemented later. This is a reference, not an implemented route in the first slice.
- Do not claim invulnerability to total storage destruction, backups loss, or incorrect database durability configuration.

## Idempotency
- Unique constraint on `(tenant_id, idempotency_key)`.
- Compute a SHA-256 fingerprint of the **raw UTF-8 request body bytes** plus the meaningfully relevant request content type (application/json), explicitly treating different whitespace/order as different requests in v0.1. No ambiguous JSON canonicalization.
- Store hash, event ID and key in the **same PostgreSQL transaction** as the event.
- Same key + same fingerprint => return existing event ID and 202, no second event.
- Same key + different fingerprint => 409 and stable problem response.
- Simultaneous equal-key requests are resolved by the database uniqueness constraint, **not** a check-then-insert race. After a unique violation, retrieve the existing committed record and compare fingerprints; handle transaction rollback correctly.
- Retain keys indefinitely in the initial learning implementation, with a future retention-policy ADR required before a public release. Do **not** add unsafe expiry automation.

## Error contract
- 400 missing/malformed key, invalid JSON, invalid type, absent payload; 413 oversized JSON body; 415 unsupported media type; 409 key reused with different request; 503 database unavailable/transaction cannot be confirmed; 500 unexpected internal error.
- Return application/problem+json with `type`, `title`, `status`, `detail`. Do not expose stack traces or internal database information.
- A timeout or ambiguous transaction outcome means the client may retry the **same key and body**. Never say a failed response proves the event did not persist.
- Unauthorized is a planned security milestone, not part of the first local-only iteration.

## Failure cases and invariants
1. Commit succeeds, response lost: retry with same key returns same ID, no duplicate.
2. Commit fails: never return 202; event/idempotency records cannot partially commit.
3. Two simultaneous requests with same key/body: one durable event, both eventually return equivalent accepted responses (subject to service availability).
4. Same key/different body: at most one version accepted; loser gets 409.
5. Process crash after 202: committed event remains in PostgreSQL, assuming DB operational durability.
6. Destination webhook was not called as a side effect of ingestion.

## Open decisions
Authentication and tenant provisioning; delivery semantics and retry policy; retention; HTTP status query route; delivery ordering. These require separate tickets.
