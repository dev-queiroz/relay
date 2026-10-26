# Engineering Roadmap

**M0 — Specifications (Tech Lead):** ingestion contract, domain model, reliability matrix, ADR, delivery handbook. Complete when docs are on `main`.

**M1 — Core API (Engineer):**
1. Scaffold Spring Boot and Java build with a health test.
2. Set up PostgreSQL, Flyway migration, Testcontainers.
3. Implement event validation and domain model.
4. Implement atomic acceptance and idempotency under concurrency.
5. Expose HTTP ingestion and verify contract.

**M2 — Delivery (later):** endpoint configuration, durable delivery scheduling, HTTP worker, retries, recovery and dead-letter handling.

**M3 — Security (later):** API keys, authorization, HMAC signatures, SSRF and network egress protection, rate limiting.

**M4 — Operations (later):** metrics, tracing, structured logs, deployment, failure drills, load benchmarks.

**M5 — Product validation (later):** TypeScript SDK and first pilots. UI and billing only with validated need.

**Scope policy:** only the next implementation slice receives ready-for-dev tickets. Subsequent stages remain roadmapped until dependencies are met.
