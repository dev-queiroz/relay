# Relay Public API Contract — v1 (M5)

- **Status:** Approved design, **not fully implemented**. Owner: Tech Lead. Date: 2026-10-31.
- **Normative definition:** [OpenAPI 3.1](../api/public-v1.yaml). This document explains invariants and rollout decisions.
- **Existing implementation on `main`:** `POST /v1/events` with `X-Relay-Api-Key` authentication, 202 + `Location`, tenant rate limiting, and problem responses. All other routes below are **proposed work for #47/#48**, not yet live.
- **Dependencies:** `event-ingestion-contract.md`, `webhook-delivery-contract.md`, `threat-model.md`, ADR-0002.

## 1. Base conventions

- Base URL: origin provided by deployment, path prefix `/v1`. No host is committed or publicly advertised yet.
- JSON over HTTPS in nonlocal environments; UTF-8; `Content-Type: application/json` for JSON requests.
- Every endpoint is tenant scoped using the authenticated **`X-Relay-Api-Key`** header. The tenant **must never** come from a body, URL, or client-controlled query parameter. No public key issuance route in v1.
- M3 currently has tenant-scoped API keys but no independently verified scope/role model. **No per-action API-key scopes are promised in v1**. Future scope support requires an explicit ADR, backwards-compatibility policy, and authorization tests. The #47 issue must not claim scopes are already enforced.
- IDs are UUID strings. Timestamps use ISO 8601 UTC `date-time` strings. Resource IDs are server-generated. Fields not in the input schema are rejected for mutations, avoiding ignored authorization-like fields.
- `X-Request-Id` may be supplied as a UUID for troubleshooting; invalid values are replaced with a generated UUID. The response should echo the effective ID on every public operation (implementation ticket).
- Error shape: `application/problem+json`, with `type`, `title`, `status`, `detail`, optional `instance`; never expose keys, signing secrets, payload bytes, internal URLs, SQL or stack traces. Stable documented `type` URIs; clients should primarily inspect HTTP status.
- Supported codes as applicable: 400 invalid query/JSON/UUID/cursor, 401 invalid/missing API key, 404 absent or other-tenant resource (avoid existence oracle), 409 conflict, 413 oversized payload, 415 unsupported request type, 429 tenant throttled with `Retry-After`, 503 dependency unavailable, 500 unexpected error. 403 reserved for a future permission/scope system.
- `Location` is a URL path to a resource, not a guarantee that the GET route existed before M5.

## 2. Endpoint management (#47)

**Routes (all planned):**

| Method | Path | Purpose |
|---|---|---|
| POST | `/v1/endpoints` | Register destination; 201 + Location + endpoint metadata + **signingSecret returned once** |
| GET | `/v1/endpoints` | Paginated tenant-owned endpoint list |
| GET | `/v1/endpoints/{endpointId}` | Fetch metadata, never signing secret |
| PATCH | `/v1/endpoints/{endpointId}` | Partial update: name, destinationUrl, enabled |
| POST | `/v1/endpoints/{endpointId}/signing-secret/rotate` | Rotate immediately; return new secret once |

Endpoint metadata: `id`, `name` (1–128, trimmed nonblank), `destinationUrl` (HTTPS, public routable only), `enabled` (boolean), `createdAt` (UTC). No client-provided tenantId. `PATCH` must contain at least one supported writable field. Invalid destination URL yields 400 with generic detail; no DNS or internal address disclosure.

**Secret provisioning:** M2 currently generates a signing secret during endpoint registration but does not return it from the service. #47 must adjust the application boundary to return the generated plaintext **only on initial POST** and **only on rotation**; store only encrypted secret. Responses carrying plaintext secret require `Cache-Control: no-store` and must never be logged. A client losing the one-time response must rotate to obtain a new secret. Retired secret is invalid immediately (existing signing policy). Do not expose secret via GET/list.

**Destination changes:** a delivery scheduled before a PATCH retains its immutable destination snapshot; later events use the new registered destination. Disable prevents *new* delivery scheduling but does not automatically delete or cancel existing queued deliveries; cancellation policy is a separate future decision. No retroactive delivery when enabled.

**Deletion deferred:** No DELETE endpoint in initial public beta because the existing schema has historical FK references. Mark `enabled=false` to stop new scheduling. Soft deletion with retention/audit belongs in #53 and a later schema migration.

## 3. Status and history (#48)

**Read-only routes (all planned):**

| Method | Path | Returns |
|---|---|---|
| GET | `/v1/events` | Paginated event summaries, newest first |
| GET | `/v1/events/{eventId}` | Event metadata and status `ACCEPTED` |
| GET | `/v1/events/{eventId}/deliveries` | Paginated deliveries for an event |
| GET | `/v1/deliveries/{deliveryId}` | One delivery, state, counts, next attempt |
| GET | `/v1/deliveries/{deliveryId}/attempts` | Paginated sanitized attempt history |

Summary fields:
- Event: `id`, `type`, `status` (`ACCEPTED`), `createdAt`. No raw `payload` in beta read endpoints; ingestion still accepts payload and dispatcher sends it.
- Delivery: `id`, `eventId`, `endpointId`, `state` (`PENDING | IN_FLIGHT | RETRY_WAIT | SUCCEEDED | EXHAUSTED`), `attemptsCount`, `nextAttemptAt` (nullable for terminal), `createdAt`, `updatedAt`. Never return lease tokens, destination URL snapshots or signing material.
- Attempt: `id`, `deliveryId`, `sequenceNumber`, `startedAt`, `finishedAt` nullable, `outcome` nullable, `responseStatus` nullable, `sanitizedErrorCode` nullable. Never include raw response body or upstream request headers.
- All reads filter tenant ownership in SQL **before pagination**; another tenant's ID must return identical 404 behavior to a nonexistent ID.
- Event status does **not** become DELIVERED when a delivery succeeds; each delivery tracks its own state.

## 4. Pagination (normative)

Applies to all list endpoints above.
- Request: optional `limit` integer 1..100, default 20; optional `cursor` opaque token. Unknown query parameters => 400.
- Response: `{ "data": [ ... ], "page": { "hasMore": boolean, "nextCursor": string | null } }`.
- Order: endpoints/events/deliveries by `created_at DESC, id DESC`; attempts by `sequence_number ASC, id ASC`. Use index-aware keyset comparisons, **not OFFSET**.
- Retrieve `limit+1` rows to set `hasMore`. Return `nextCursor=null` when no next page. Empty page is `data: []`, `hasMore: false`.
- Cursor encodes the last returned sort tuple, route/filters, authenticated tenant identity and cursor version; sign using a distinct server-managed HMAC key. Cursor is URL-safe Base64, opaque to consumers, expires after 24 hours. Signature mismatch, expiry, cross-route, filter or tenant reuse => 400, never 500. Do not log cursor contents.
- Freeze the order, **not** a database snapshot across calls. Concurrent writes may appear on earlier pages; keyset pagination prevents duplicates in a stable ordering but cannot guarantee a point-in-time export. Document this behavior.
- No public `page`, `offset`, `sort`, tenant filter or raw SQL filters in v1.

## 5. Existing ingestion contract (must not regress)

`POST /v1/events`: required `Idempotency-Key` UUID and `X-Relay-Api-Key`; JSON object `{type,payload}` with 64KiB max body. Returns 202 `{eventId,status:"accepted"}` and `Location: /v1/events/{eventId}`. Same key+same raw-body fingerprint returns same event; same key+changed request 409. Body-byte changes count as different requests. A timeout may have committed; retry same key and same bytes. Rate limiting returns 429. No exactly-once downstream guarantee.

## 6. Compatibility and releases

- New additive nullable response fields are permissible within `/v1`. Removed fields, changed types, changed enum meaning, different pagination ordering, auth header changes and changed error semantics are breaking; require `/v2` or explicit migration path.
- Unknown response fields should be ignored by clients. Unknown **request** fields on mutation routes are rejected. Endpoint status values can expand only through a documented change.
- OpenAPI lists planned operations as such in their descriptions; do **not** publish as a live public API reference until #47/#48 integration tests pass.
- `POST /v1/endpoints` and secret rotation are not safe to blindly retry after a lost response; clients must query endpoint metadata or initiate an explicit rotation. No idempotency behavior is promised for these routes in v1.
- SDK #49 must bind to implemented endpoints only and implement safe retry rules.

## 7. Acceptance gates / issue map

- **#46 (Tech Lead):** document public contract and OpenAPI; state planned vs existing; paginate and security policy; resolve ambiguities. Documentation complete upon commit.
- **#47 (Engineer):** endpoint management routes, one-time signing secret and tenant isolation. No hard delete. Verify forbidden URL, secret disclosure, update snapshot behavior, 401/404/400/429/503.
- **#48 (Engineer):** status and history routes, keyset pagination, HMAC bound cursor, response redaction; test cross-tenant, concurrent insert behavior, cursor tampering, expired cursor, page size boundaries.
- **#49 (Engineer):** TypeScript SDK using existing and verified contract; no unsupported endpoint calls.
- **#53 (Engineer):** tenant lifecycle, audit and any future endpoint soft deletion.
