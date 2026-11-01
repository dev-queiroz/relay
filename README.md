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

See [the reproducible developer quickstart](docs/engineering/quickstart.md) for API authentication, endpoints, delivery verification, retries and status inspection.

### Prerequisites

- Java 25 (JDK)
- Git
- Docker with the Compose plugin (for PostgreSQL and Testcontainers)
- Node.js 20+ (for the ESM TypeScript SDK)

The project uses the Maven Wrapper, so a global Maven installation is not required.

### Clone the Repository

```bash
git clone https://github.com/dev-queiroz/relay.git
cd relay
```

### Run Locally

Start the local PostgreSQL instance:

```bash
docker compose up -d db
```

The Compose database is for local development only. Export the datasource values
before starting the application; do not commit personal or shared credentials.

On Windows PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/relay"
$env:DB_USERNAME = "relay"
$env:DB_PASSWORD = "relay"
$env:POSTGRES_PASSWORD = "relay"
$env:POSTGRES_USER = "relay"
$env:RELAY_SECRETS_ENCRYPTION_KEY = "<base64-encoded 32-byte key>"
```

On Linux or macOS:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/relay
export DB_USERNAME=relay
export DB_PASSWORD=relay
export POSTGRES_PASSWORD=relay
export POSTGRES_USER=relay
export RELAY_SECRETS_ENCRYPTION_KEY='<base64-encoded 32-byte key>'
```

If port 5432 is already in use, start Compose with `POSTGRES_PORT` set to an
available local port and use that same port in `DB_URL`.

On Windows PowerShell:

```powershell
.\mvnw.cmd spring-boot:run
```

On Linux or macOS:

```bash
./mvnw spring-boot:run
```

The application starts at `http://localhost:8080`.

The delivery worker is disabled by default so that an API-only deployment does not
claim deliveries. Enable it in the worker deployment with
`RELAY_WORKER_ENABLED=true`; set `RELAY_WORKER_HEALTH_REQUIRED=true` there when
readiness should require a recent worker heartbeat.

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
