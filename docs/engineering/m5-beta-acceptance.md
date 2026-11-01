# M5 beta acceptance report

## Evidence reviewed

- M3: tenant API-key authentication, endpoint signing-secret encryption, SSRF controls and the adversarial security gate.
- M4: structured correlation, metrics, readiness and tracing coverage.
- M5: public endpoint and status APIs, signed receiver reference, TypeScript SDK, quickstart and pilot-feedback guide.

## Beta gate

Enable a pilot only after the current `main` CI is green, migration backups are verified, runtime secrets are supplied outside source control, and a pilot owner accepts at-least-once delivery semantics. The receiver must validate signatures and deduplicate delivery IDs.

## Known limits

There is no SLA, billing system, public tenant provisioning, hard-deletion workflow, production retention commitment or point-in-time pagination promise. A lost response to endpoint creation or secret rotation remains ambiguous and must be handled through explicit inspection or rotation.

## Release decision

**Conditional beta approval.** The technical controls support an invitation-only pilot. Production expansion requires measured pilot feedback, the approved tenant/retention lifecycle, operational ownership and a separate commercial decision.
