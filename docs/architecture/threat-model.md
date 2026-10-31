# M3 threat model and acceptance gates

## Trust boundaries

Clients, endpoint URLs, event payloads, API keys, HTTP responses, DNS answers and logs are untrusted. PostgreSQL is the system of record; workers and receivers are separate trust domains.

## Threats and required gates

| Threat | Required M3 control | Verification |
|---|---|---|
| Tenant escalation | Tenant-scoped authenticated context on every query/command | Cross-tenant negative tests |
| Key disclosure/replay | Hashed, rotatable keys and constant-time verification | Revoked/expired-key tests |
| SSRF/DNS rebinding | HTTPS, address policy, DNS re-resolution and egress controls | Private/metadata/redirect/rebinding tests |
| Payload or secret logging | Redacted logs and safe errors | Log inspection tests |
| Request replay | HMAC timestamp/signature verification | Replay and tamper tests |
| Abuse | Distributed rate and payload limits | Burst-limit tests |

## Release gate

No public endpoint registration or public dispatch is permitted until every control above has automated evidence. M2's local allowlist is not an SSRF defence and must not be used as a production security claim.

## Implemented destination gate

Endpoint registration accepts only HTTPS URLs without credentials or fragments. Every DNS answer must be publicly routable; loopback, private, link-local, multicast, carrier-grade NAT, benchmark and IPv6 local ranges are rejected. The same DNS check runs again immediately before dispatch, and the HTTP client does not follow redirects. Deployment egress controls must still permit outbound HTTPS only to public Internet ranges: application validation is defence in depth, not a substitute for the network boundary.

## Secret-handling rules

Production configuration obtains database credentials and `RELAY_SECRETS_ENCRYPTION_KEY` from the runtime environment; there are no production fallbacks. The encryption key is a base64-encoded 32-byte AES key. Endpoint signing secrets are encrypted with AES-256-GCM before persistence and decrypted only for dispatch. API keys, signing secrets, authorization headers and payloads must not be logged. HTTP error responses use fixed safe messages and omit exception details and stack traces. CI scans the repository for committed secrets and rejects configuration credential defaults.

Operational logs use structured JSON and may include only the request, tenant, event, delivery and response-status correlation fields. An incoming request ID is accepted only when it is a UUID; otherwise Relay creates one. Payloads, destinations, credentials, headers, raw exceptions, and signing material are prohibited from operational log fields.
