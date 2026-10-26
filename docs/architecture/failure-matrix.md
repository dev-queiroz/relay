# Reliability / Failure Matrix

| Failure | Contract response | Persistence outcome | Verification |
|---|---|---|---|
| DB unavailable before commit | 503 | None guaranteed | Stop DB, call API |
| DB outcome ambiguous to API | 503 or timeout | May or may not have committed | Retry same key |
| API crash after commit | No response or 202 | Durable if DB satisfies assumed guarantees | Kill API and retry |
| Duplicate sequential submission | 202 with original ID | One event | Repeat request |
| Duplicate concurrent submission | 202 same ID, or transient failure eligible for retry | At most one accepted key | Parallel integration test |
| Same key changed body | 409 | Original event unchanged | Change request |
| Invalid input | 400 / 413 / 415 | No event | Negative tests |
| Worker crash (future) | N/A | Delivery pending/recoverable | Restart worker test |
| Destination timeout (future) | N/A | Outcome unknown; retry may duplicate downstream side effects | Fake HTTP server |

**Limit of guarantee:** at-least-once webhook delivery is a future design goal; never claim exactly-once processing. SSRF protection, signatures and tenant-specific authorization are public-release blockers.
