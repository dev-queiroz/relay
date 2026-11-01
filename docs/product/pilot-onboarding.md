# Pilot onboarding and feedback

## Entry criteria

Invite teams that own a public HTTPS receiver, can store a signing secret safely, and accept at-least-once delivery semantics. Do not enroll workloads carrying regulated or production-critical data until an owner has approved their risk assessment.

## Onboarding interview

Record the integration goal, event volume range, receiver technology, retry expectations, owner contact and rollback contact. Demonstrate endpoint creation, one-time secret storage, an idempotent event retry, signature verification and delivery-status inspection. Never collect API keys, signing secrets or payloads in interview notes.

## Success measures

- Time from invitation to a verified signed delivery.
- Successful delivery percentage and exhausted-delivery count.
- Number of integrations that correctly retry an ambiguous ingestion outcome with the same idempotency key and bytes.
- Receiver duplicate-handling outcome and operator-reported integration friction.

## Feedback capture

Store feedback under a random pilot code, not a tenant ID. Separate contact details from technical notes. Capture date, workflow step, observation, severity, evidence link and requested follow-up. Redact headers, secrets, customer payloads and internal destination addresses before sharing summaries.

## Exit and rollback

Participants can disable an endpoint to stop new scheduling. Deactivation and data deletion follow the tenant lifecycle, not ad-hoc pilot notes. The pilot does not establish an SLA, data-retention promise or billing commitment.
