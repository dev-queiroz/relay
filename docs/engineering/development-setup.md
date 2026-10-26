# Developer Environment and Delivery

## Requirements
- Java 25 JDK; run `java -version`
- Maven or Maven wrapper (to be added by developer in first implementation PR)
- Docker with Compose plugin
- Git

## Workflow
1. Claim a `ready-for-dev` issue and follow linked specifications.
2. Branch from `main`: `feat/<issue-number>-short-name`.
3. Implement code and tests in that branch only.
4. Run `mvn verify` and integration tests per PR instructions once the build exists.
5. Open PR using the repository template; include `Closes #N`.
6. Tech Lead reviews; engineer fixes feedback, then merges.

## Project conventions
- Package root proposed: `dev.queiroz.relay`.
- Use Maven modules only when justified; start single module.
- Never store secrets in Git; use `.env.example` for nonsecret variables.
- Prefer meaningful exceptions and predictable HTTP status mapping.
- Test domain invariants and failures, not merely the happy path.
- JUnit tests in `src/test/java`; integration tests may use Testcontainers.
- Developers should not modify accepted architecture documents without a PR rationale.

## Production caveat
Free local development does not imply free 24/7 production operation. No public endpoints before authentication, abuse protection, and SSRF controls.
