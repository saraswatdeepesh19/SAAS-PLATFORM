# SaaS Platform

Java 21 / Spring Boot Maven monorepo for the multi-tenant usage and billing platform.

For a code-linked architecture walkthrough and interview preparation, see [Technical Interview Guide](md/TECHNICAL_INTERVIEW_GUIDE.md).

## Stage 1 services

- `common`: shared event, plan, role, topic, and JWT claim contracts
- `auth-service`: tenant/user persistence, BCrypt, HS256 JWTs, registration, login, and tenant events
- `device-service`: tenant-isolated device/session APIs with a transactional outbox
- `usage-service`: idempotent session event consumption, monthly aggregation, summary API, retries, and DLT
- `billing-service`: event-fed plans/snapshots, three pricing strategies, invoice API, and invoice events
- `notification-service`: idempotent invoice email delivery through MailHog
- `api-gateway`: JWT validation, routing, per-tenant in-memory rate limiting, and trace IDs

Services use ports 8080-8085 as described in the master plan. Each business service owns a separate PostgreSQL database. Kafka remains internal at `kafka:9092`; host-run apps use `localhost:9094`.

## Prerequisites

- JDK 21
- Maven 3.9+
- Existing local infrastructure from `infra/docker-compose.yml` for service integration work

## Build

From the repository root:

```powershell
mvn verify
```

Build and start the full Stage 1 stack from the repository root with:

```powershell
docker compose -f infra/docker-compose.yml up -d --build
```

The Compose setup exposes Kafka on `localhost:9094` for services run directly on the host, while retaining `kafka:9092` for containers on the Compose network. App health endpoints are exposed at `/actuator/health`; Prometheus scrapes each app's `/actuator/prometheus` endpoint.

## Logs and distributed tracing

Open Jaeger at `http://localhost:16686`. Services export traces using OTLP/HTTP to `http://jaeger:4318/v1/traces`. Logs include the service name, `traceId`, and `spanId`; HTTP uses W3C `traceparent`, and Kafka producer/listener observations propagate context across events. The device outbox stores the originating `traceparent` and restores it for scheduled publication. The gateway's `X-Trace-Id` response/request correlation header aligns with an incoming W3C trace ID when supplied.

Tracing uses Spring Boot's Micrometer Observation/Tracing integration with the OpenTelemetry bridge. This keeps HTTP, Kafka, and logging instrumentation aligned with Spring while OTLP remains vendor-neutral; Jaeger is only the local collector/UI and can be replaced without changing service instrumentation. Local sampling defaults to 100%; lower `TRACING_SAMPLING_PROBABILITY` (for example, `0.1`) for production traffic volume.

## Auth service

The PostgreSQL init script creates one database per service when the Compose volume is first initialized. If the current PostgreSQL volume predates the init script, create the remaining databases once (auth_db was provisioned during setup):

```powershell
docker compose -f infra/docker-compose.yml exec postgres psql -U saas -d saas -c "CREATE DATABASE device_db" -c "CREATE DATABASE usage_db" -c "CREATE DATABASE billing_db" -c "CREATE DATABASE notification_db"
```

Set `JWT_SECRET` to a private value of at least 32 bytes outside local development. Start the service with:

```powershell
mvn -f auth-service/pom.xml spring-boot:run
```

The auth endpoints are `POST /auth/register-tenant`, `POST /auth/login`, `POST /auth/users`, and `GET /auth/me`. Registration and login are public; creating users requires `ROLE_ADMIN`, and `/auth/me` requires a valid bearer token.

## End-to-end check

With the Compose stack healthy, run the Stage 1 lifecycle check from Bash or Git Bash (requires `curl` and `jq`):

```bash
bash scripts/e2e.sh
```

The check registers a flat-rate tenant, creates and ends a device session, waits for usage aggregation, generates an idempotent invoice, and confirms its email appears in MailHog.

On Windows PowerShell, run the native equivalent without changing the machine execution policy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\e2e.ps1
```