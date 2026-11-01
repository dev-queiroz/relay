# ADR-0003: Pricing and metering remain evidence-gated

- Status: Accepted for pilot instrumentation; commercial pricing deferred.
- Date: 2026-11-01

## Context

Relay persists accepted events, delivery attempts and tenant identity. These are candidates for usage measurement, but no pilot evidence yet establishes willingness to pay, an acceptable unit, free-tier limits, support cost or provider fees.

## Decision

Record two auditable, non-billable counters per tenant: accepted events and completed delivery attempts. Count an event once at durable acceptance; count an attempt once when its persisted attempt record reaches a terminal outcome. Retries are distinct attempts and must not create another event charge. Reconciliation compares meter rows with PostgreSQL event and delivery-attempt records by tenant and reporting window.

Do not expose prices, free-tier commitments, invoices, subscriptions or billing-provider integrations until pilot feedback validates commercial need and a pricing review approves units, limits and taxes. Existing ingestion limits remain abuse controls, not a commercial plan.

## Alternatives considered

- Price per delivery only: hides upstream acceptance and makes retry behavior a pricing dispute.
- Price per event only: ignores a primary infrastructure cost driver.
- Start with a public free tier: creates an unsupported commercial promise before cost evidence.

## Consequences

Future metering must be tenant-scoped, append-only or reconciled from source records, and must disclose corrections. It must not meter payload bytes, secret material or raw destination data. The decision does not authorize #57–#60; they remain blocked by pilot validation and approved billing/retention contracts.
