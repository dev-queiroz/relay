# Relay

**A Java-first, developer-focused platform for reliable webhook delivery.**

> **Status:** M0 — Product discovery and engineering foundations. No code or production guarantees yet.

## Problem

Applications need to deliver events to third-party HTTP endpoints despite downtime, timeouts, duplicate submissions, and process crashes. Relay will explore durable event ingestion, delivery scheduling, retries, endpoint security, and operability.

## Principles

1. **Evidence over claims.** Reliability, security, and performance guarantees require tests.
2. **Engineer owns implementation.** Design, production code, and tests are written and understood by the Senior Backend Engineer.
3. **Tech Lead owns the process.** Scope, acceptance criteria, technical challenge, PR reviews, and documentation.
4. **Start simple.** Begin with Java domain modeling; introduce Spring Boot and PostgreSQL as the requirements demand. TypeScript may be used later for a customer SDK.
5. **English throughout.** Issues, documentation, commits, and pull requests use English.

## Milestones

- **M0 — Discovery:** event acceptance contract, failure modes, domain language, and decisions.
- **M1 — Core:** Java domain model, API, and durable persistence.
- **M2 — Delivery:** background processing, attempts, retries, and recovery.
- **M3 — Security:** authentication, isolation, signatures, and SSRF defenses.
- **M4 — Operations:** observability, integration tests, load tests, runbooks.
- **M5 — Product validation:** TypeScript SDK, documentation, and real pilot users.

These milestones are tentative, not release commitments.


## Development

### Prerequisites

- Java 25 (JDK)
- Git

The project uses the Maven Wrapper, so a global Maven installation is not required.

### Clone the Repository

```bash
git clone https://github.com/dev-queiroz/relay.git
cd relay
```

### Run Locally

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux or macOS:

```bash
./mvnw spring-boot:run
```

The application starts at `http://localhost:8080`.

### Health Check

Once the application is running:

```powershell
curl.exe -i http://localhost:8080/actuator/health
```

Expected HTTP status: `200 OK`

Expected response:

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

### Run Tests

On Windows PowerShell:

```powershell
.\mvnw.cmd clean verify
```

On Linux or macOS:

```bash
./mvnw clean verify
```

A successful execution should finish with `BUILD SUCCESS`.

### Continuous Integration

GitHub Actions automatically compiles the application and runs its tests using Java 25 on pull requests targeting `main` and pushes to `main`.

## Start here

1. Read [Engineering Handbook](docs/engineering/handbook.md).
2. Read [Product Brief](docs/product/brief.md).
3. Pick up [the first engineering task](https://github.com/dev-queiroz/relay/issues).
4. Submit a pull request with reasoning and verification evidence.

**First question:** What precisely does `POST /v1/events` promise when it returns `202 Accepted`?
