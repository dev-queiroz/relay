# Product Brief — Relay

## Target customer
Small SaaS teams and independent backend developers who deliver outbound webhooks and need understandable, inspectable delivery behavior.

## Problem
Naive HTTP delivery loses events on crashes, duplicates processing on ambiguous responses, and makes failure diagnosis difficult.

## Hypothesis
A focused webhook delivery API with trustworthy retries and transparent delivery history can reduce application maintenance work.

## Proposed minimum scope
- Authenticated event ingestion with a defined acceptance guarantee.
- Durable storage of accepted events and configured deliveries.
- Background HTTP delivery and attempt history.
- Retry policy and dead-letter/manual replay behavior.
- Endpoint configuration, signed requests, tenant isolation.
- Minimal operational visibility.

## Explicit non-goals for the first release
- Kafka-based microservices by default.
- Distributed exactly-once processing claims.
- A comprehensive billing engine.
- A UI before the API is verifiably useful.

## Unvalidated assumptions
- Target users have a problem serious enough to pay for.
- A simple developer experience will provide differentiation.
- Operational cost will support sustainable pricing.

## Validation
Interview at least five potential users, record their current workflow and failure pain, and seek a pilot before investing in billing.
