# M3 security verification gate

## Required CI evidence

The `m3-security-gate` GitHub Actions job is required before any public deployment. Deployment workflows and branch protection must require this check; a failing or missing check blocks release promotion.

The gate runs `scripts/run-m3-security-gate.sh`, which covers:

- API-key authentication and tenant-header spoofing;
- tenant burst limits, daily quotas, and per-endpoint worker concurrency;
- private, metadata, mixed DNS-answer, and rebind SSRF targets;
- HMAC byte tampering, stale/missing timestamp, replay-window, and rotated-secret verification;
- encryption-key validation and protected secret storage; and
- configuration defaults and unsafe application logging calls.

## Staging checklist

Run after setting non-test `DB_*`, `POSTGRES_*`, and `RELAY_SECRETS_ENCRYPTION_KEY` values in the staging secret manager:

1. Run `./scripts/run-m3-security-gate.sh` and retain the CI run URL.
2. Submit a request with no API key and confirm a safe `401` response.
3. Submit a burst exceeding the configured tenant limit and confirm `429` plus `Retry-After`, then confirm a different tenant remains accepted.
4. Register loopback, link-local metadata, private IPv4, and unique-local IPv6 destinations; each must be rejected. Confirm a DNS record changed from public to private is rejected at dispatch.
5. Deliver a signed event, verify `X-Relay-Timestamp` and `X-Relay-Signature` against the exact bytes, then verify modified bytes, stale timestamp, replayed delivery ID, and retired secret are rejected by the receiver.
6. Inspect application logs and HTTP errors for the test run: no API key, secret, Authorization header, or payload is present.

## Evidence record

Staging access is not available in the repository or CI environment. The release owner records the date, environment, CI run URL, receiver verification result, and log-inspection result in the deployment change record before public promotion. Do not treat local or unit-test results as staging evidence.
