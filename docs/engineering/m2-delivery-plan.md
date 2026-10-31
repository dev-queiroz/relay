# M2 — Implementation Plan

Owner of design/docs/issues: Tech Lead. Owner of code/tests/PRs: Backend Engineer.

1. **M2-01 — Persistence foundation (ready):** Flyway migration for endpoints, deliveries and attempts, tenant-safe keys and indexes; Testcontainers schema tests. No workers or outbound HTTP.
2. **M2-02 — Endpoint configuration (blocked by M2-01):** internal dev-only endpoint registration/lookup, URL validation and explicit allowlisted fixture targets.
3. **M2-03 — Atomic scheduling (blocked by M2-01/02):** same-transaction event acceptance + deliveries, idempotent retry no duplicate scheduling.
4. **M2-04 — Claims and leases (blocked by M2-03):** SKIP LOCKED, lease token fencing, reclaim expired leases, concurrency tests.
5. **M2-05 — HTTP dispatcher (blocked by M2-04):** mock destination calls, stable headers, timeouts, redirects disabled and outcome recording.
6. **M2-06 — Retry/recovery (blocked by M2-05):** retry schedule, exhausted outcomes, process-crash tests and an M2 integration demonstration.

**Review requirements:** documented invariants and test evidence per PR; Codex Bot findings resolved; Tech Lead reviews for contract compatibility. M2 is development-only; public deployment requires M3 safety gates.

References: `docs/adr/0002-delivery-architecture.md` and `docs/architecture/webhook-delivery-contract.md`.
