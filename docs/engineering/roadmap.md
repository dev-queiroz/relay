# Relay — Delivery-to-Product Roadmap

Owner: Tech Lead. Implementation: Backend Engineer. Engineering language: English.

## Milestones and release gates

| Stage | Goal | Issue range | Gate |
|---|---|---|---|
| M0 | Architecture and initial decisions | #1–#4 | Completed |
| M1 | Core ingestion service | #6–#10 | Completed in GitHub |
| M2 | Reliable webhook delivery | #16–#22 | Local end-to-end delivery, recovery and deterministic retry tests |
| M3 | Public security boundary | #23–#30 | SSRF, authentication, tenant isolation, signatures and abuse tests |
| M4 | Operations and production hardening | #31–#36, #43–#45 | Benchmarks, staging, runbooks, alerts, release checks |
| M5 | Developer experience and beta | #46–#54 and #5 | Public API contract, docs, SDK, beta evidence |
| M6 | Commercial readiness | #55–#60 | Validated pricing, metering, optional billing, cost governance |

Note: #37–#42 were duplicate M4 tickets and closed as duplicates. The issue titles carry milestone and priority prefixes, but GitHub **native milestone association is still pending**; create/associate native milestones once authenticated management is available.

## Execution order

**Critical path:**
#16 -> #17 -> #18 -> #19 -> #20 -> #21 -> #22 -> #23 -> #24/#25/#26/#27/#28/#29 -> #30 -> #43 -> #54.

**Parallel tracks:**
- M3 documentation (#23) may begin during M2, but unsafe production dispatch must not.
- M4 log/metrics/trace and backup preparations (#31–#34) may proceed after M2 stable schema.
- M5 customer API design (#46), SDK design and customer discovery (#5/#52) may begin in parallel.
- Billing (#57) and usage (#56) require approved pricing/metering (#55) and pilot evidence.

## Priorities
P0 = required for safe working service; P1 = required for operable beta; P2 = commercial option, not necessarily needed for pilot.

## Repository conventions
- Engineer owns code, tests, PR. Tech Lead owns architectural documents, requirements and review.
- Each issue needs reproducible checks; CI and Codex review findings resolved.
- Finish one PR before starting a dependent issue.
- No public network exposure before passing M3 security gates.
- No unverified exactly-once claim. Postgres is the source of truth.
- Avoid unnecessary microservices, queues, dashboard, or billing before clear evidence.

## Shipping criteria
A local demo is not a public release. A release requires security tests, operational controls, restore drill, service metrics and realistic limits. SaaS billing is deferred until value is validated.
