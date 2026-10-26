# ADR-0001 — Java Modular Monolith and PostgreSQL

- Status: Accepted for M1, revisit before high-scale production
- Date: 2026-10-26
- Owners: Tech Lead / Senior Backend Engineer

## Context
Relay needs a small, inspectable vertical slice with strong ingestion consistency and low local operating cost. The engineer is migrating to Java.

## Decision
Use Java 25 LTS, Maven, Spring Boot 4.x, and PostgreSQL for the first API. Organize packages by business capability and isolate domain rules from framework classes where practical. Start with one deployable process; do not create independent services or queue infrastructure at launch. Flyway owns schema changes. Add testing using JUnit and Testcontainers; use Docker Compose locally.

Persist Event and IdempotencyRecord atomically in PostgreSQL with a tenant-scoped unique constraint. Avoid Java in-memory queues for durable acceptance.

## Considered options
1. In-memory ingestion: trivial but loses accepted events on process crash; rejected.
2. Redis/broker before PostgreSQL: added operations and cross-system consistency without a demonstrated requirement; deferred.
3. Kafka microservices: complexity and cost not justified; rejected for M1.
4. PostgreSQL transaction in modular monolith: selected for clarity and verifiable correctness.

## Consequences
An unavailable PostgreSQL blocks acceptance. Throughput is bounded by database transaction performance. Backpressure, multi-node workers, and operational HA need future designs.

## Revisit triggers
Real measurements indicate throughput bottlenecks; customer uptime/SLA requirements; database operational cost; multiple worker replicas; public multi-tenant deployment.
