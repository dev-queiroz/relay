# Relay quickstart

Requires Java 25, Node 20+, Docker and environment-provided `DB_*`, `RELAY_SECRETS_ENCRYPTION_KEY` and `RELAY_CURSOR_HMAC_KEY` values. Start PostgreSQL with `docker compose up -d db`, then run `./mvnw spring-boot:run`.

Obtain a tenant API key through provisioning (there is intentionally no public key-issuance route). Send it only as `X-Relay-Api-Key`; never put a tenant ID in requests.

Create an HTTPS public endpoint with `POST /v1/endpoints`. Store the returned `signingSecret` immediately: the no-store response is the only creation-time disclosure. Send `POST /v1/events` with an `Idempotency-Key` UUID and JSON `{ "type": "invoice.created", "payload": {} }`. On a timeout retry the exact same bytes and key; a 202 may already have committed. Do not retry endpoint creation or secret rotation blindly.

Use `GET /v1/events`, `/v1/events/{id}/deliveries`, `/v1/deliveries/{id}` and `/v1/deliveries/{id}/attempts` for status. Preserve opaque cursors unchanged. A 429 provides `Retry-After`; 503 and network failures may be retried only for idempotent ingestion. 400/401/404 problem responses never expose secrets, payloads or destination snapshots.

Receiver implementations must verify `X-Relay-Timestamp` and `X-Relay-Signature` against `timestamp + "." + exact request bytes`, reject stale timestamps, and deduplicate `X-Relay-Delivery-Id`. See `docs/engineering/receiver-reference.md`.

Run `npm ci && npm run build && npm test` for the SDK and `./mvnw verify` for Relay. The public API is versioned under `/v1`; additive response fields may appear, but clients must treat unknown request fields as errors.
