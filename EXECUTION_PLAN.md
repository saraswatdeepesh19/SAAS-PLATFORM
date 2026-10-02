# SaaS Platform Execution Plan

This document captures the execution plan derived from the master plan in `SAAS_PLATFORM_MASTER_PLAN.md`.

## 1. Objective
Build a multi-tenant cloud-native SaaS platform in staged phases:
- Stage 1: local Docker Compose foundation
- Stage 2: Kubernetes/local orchestration
- Stage 3: AWS EKS deployment

The platform will follow the architecture defined in the master plan:
- auth → device → usage → billing → notification → gateway
- shared contracts in a common module
- database-per-service
- Kafka-based asynchronous event flow
- tenant isolation with `tenant_id`

## 2. Strategic Principles
- Build in dependency order, not by feature convenience.
- Keep service boundaries strict: each service owns its own database.
- Use shared event contracts from the `common` module.
- Prefer reliable messaging via transactional outbox and idempotent consumers.
- Validate each stage before moving to the next.

## 3. Build Order

### Step 0: Infra foundation
- Set up Docker Compose infrastructure
- Include PostgreSQL, Kafka, MailHog, Prometheus, Grafana
- Validate health checks and connectivity
- Goal: `docker compose up` starts all infra services healthy

### Step 1: Shared contracts module
- Create `common` module
- Add constants, enums, and event records
- Publish to local Maven repo
- Goal: all services consume the same event schemas

### Step 2: Auth service
- Create tenant and user tables
- Implement registration, login, BCrypt password hashing, JWT generation
- Publish `TenantRegistered` event
- Validate tenant-aware token claims
- Goal: register/login flow works and JWT includes tenant information

### Step 3: Device service
- Create device and session tables
- Implement tenant-isolated CRUD and session lifecycle
- Add optimistic locking for device sessions
- Use transactional outbox to publish `SessionEnded`
- Goal: concurrent session start is safely handled and events are reliable

### Step 4: Usage service
- Consume `SessionEnded` messages from Kafka
- Enforce idempotency through `processed_events`
- Aggregate usage per tenant and period
- Publish `UsageAggregated`
- Add retry and DLT handling
- Goal: duplicate events are ignored and usage summaries are correct

### Step 5: Billing service
- Create tenant plan, usage snapshot, invoice, and invoice line tables
- Consume tenant and usage events
- Implement pricing strategies
- Generate invoice per tenant/period and publish `InvoiceGenerated`
- Goal: monthly invoice generation is deterministic and repeatable

### Step 6: Notification service
- Consume `InvoiceGenerated`
- Send email via MailHog SMTP
- Log notification status and dedupe by `event_id`
- Goal: invoice email successfully delivered and tracked

### Step 7: API Gateway
- Add route configuration and JWT validation
- Secure public vs private endpoints
- Add rate limiting and trace propagation
- Goal: single entry point for all service traffic

### Step 8: Dockerization and compose integration
- Multi-stage Dockerfiles for each service
- Add all services to Compose
- Add health checks and environment wiring
- Goal: one command brings up the full platform

### Step 9: E2E validation
- Write and run `scripts/e2e.sh`
- Validate the full flow across registration, login, sessions, usage, billing, and email
- Goal: end-to-end pass for Stage 1

## 4. Detailed Service Execution Sequence

### 4.1 Auth service
Responsibilities:
- Tenant registration
- User creation and login
- JWT issuance
- Kafka event publication for tenant creation

Key features:
- Flyway migration `V1__init.sql`
- `TenantRepository` and `UserRepository`
- `PasswordEncoder` using BCrypt
- `JwtService` for token generation and validation
- `SecurityConfig` with protected/unprotected routes
- `TenantRegisteredEvent` publication after transaction commit

Acceptance criteria:
- `POST /auth/register-tenant` creates tenant and admin user
- `POST /auth/login` returns valid JWT token
- JWT contains `tenantId` and roles
- duplicate tenant or email is rejected with 409

### 4.2 Device service
Responsibilities:
- Device CRUD
- Session start/end
- Tenant-safe resource access
- Reliable Kafka event publication

Key features:
- `JwtAuthFilter` sets `TenantContext`
- Repositories filter by tenant
- `@Version` optimistic locking on device
- `device_sessions` with unique active session per device
- `outbox_events` store for `SessionEnded`
- `OutboxPublisher` scheduled poller sends pending events

Acceptance criteria:
- tenant cannot access another tenant’s device
- concurrent start-session on same device yields exactly one success
- session end writes DB update + outbox entry atomically
- notification flow starts after outbox publish

### 4.3 Usage service
Responsibilities:
- Consume `SessionEnded`
- Aggregate usage by tenant and period
- Expose usage summary API
- Publish aggregated usage event

Key features:
- Kafka consumer with manual ack and retry policy
- `processed_events` table for idempotency
- `usage_records` and `usage_monthly`
- dead letter topic for poison messages

Acceptance criteria:
- duplicate `SessionEnded` events processed only once
- malformed event moves to DLT
- `GET /usage/summary` returns correct total usage for period

### 4.4 Billing service
Responsibilities:
- Tenant plan handling
- Monthly usage snapshot
- Invoice generation
- Event publication for invoice creation

Key features:
- `tenant_plans` table
- `usage_monthly_snapshot`
- `invoices` and `invoice_lines`
- `PricingStrategy` implementations
- `PricingStrategyFactory`
- `InvoiceGeneratedEvent` publication

Acceptance criteria:
- invoice generated only once per tenant/period
- `FREE_TIER`, `FLAT_RATE`, and `TIERED` calculations are correct
- invoice total uses `BigDecimal` and proper rounding

### 4.5 Notification service
Responsibilities:
- Consume invoice events
- Send email to tenant
- Record delivery status

Key features:
- `notification_log` table with unique `event_id`
- Email channel abstraction
- MailHog integration for local testing

Acceptance criteria:
- invoice event triggers an email
- duplicate event is skipped
- failed delivery is logged and retried appropriately

### 4.6 API Gateway
Responsibilities:
- Route external traffic
- Verify JWTs
- Enforce rate limits
- Propagate trace IDs

Key features:
- route to auth, device, usage, billing services
- public path whitelist for login/register
- JWT validation before upstream routing

Acceptance criteria:
- unauthenticated requests are rejected
- valid JWT requests reach the right service
- tenant-based rate limiting works

## 5. HLD Summary

The platform is designed as a set of independent services with separate databases and Kafka topic communication.

Core flow:
- `Auth` creates a tenant and emits `TenantRegistered`
- `Device` emits `SessionEnded`
- `Usage` consumes session end events and emits `UsageAggregated`
- `Billing` consumes aggregated usage and creates `InvoiceGenerated`
- `Notification` consumes invoice events and sends email

## 6. Key Design Decisions
- Java 21 + Spring Boot 3.x
- Maven monorepo with service-per-folder
- PostgreSQL per service
- Flyway migrations
- Kafka in KRaft mode
- JSON event payloads
- JWT HS256 initially, later migrating to RSA/JWKS
- MailHog for local email testing
- JUnit 5 / Mockito / Testcontainers
- Actuator + Micrometer + Prometheus + Grafana

## 7. Testing Strategy

### Unit tests
- `JwtService`
- `PricingStrategy` math
- `UsageProcessingService.validate`

### Slice tests
- controller behavior
- repository validation
- DTO mapping

### Integration tests
- Flyway migrations
- Kafka consumer idempotency
- transactional outbox publish
- DLT event flow

### Concurrency tests
- two threads starting the same device session should produce one success and one conflict

### Security tests
- cross-tenant access should return 404
- missing JWT should return 401
- wrong role should return 403

### E2E tests
- Run `scripts/e2e.sh`
- Validate the end-to-end SaaS lifecycle

## 8. Stage 2 Kubernetes Roadmap
- Create cluster with kind or minikube
- Deploy Postgres + Kafka dependencies
- Package each service as images
- Add Deployments, Services, ConfigMaps, Secrets
- Add readiness/liveness probes
- Add ingress and verify e2e through gateway
- Run resilience drills and dashboards

## 9. Stage 3 AWS Roadmap
- IAM and billing alarm setup
- ECR image repositories
- VPC and EKS cluster
- RDS PostgreSQL
- Kafka via MSK or Strimzi
- Secrets Manager + IRSA
- ALB and ACM cert via ingress
- SES for email
- GitHub Actions + Argo CD or Helm deployment pipeline
- e2e validation against public URL

## 10. Definition of Done

### Stage 1
- [ ] Compose infra healthy
- [ ] common module builds as jar
- [ ] auth service works with tenant registration and JWT
- [ ] device service supports tenant-aware CRUD and sessions
- [ ] usage service handles idempotent aggregation and DLT
- [ ] billing service generates invoices and publishes events
- [ ] notification service sends emails
- [ ] gateway secures and routes traffic
- [ ] all services Dockerized
- [ ] e2e script passes

### Stage 2
- [ ] services deploy to kind/minikube
- [ ] ingress works
- [ ] resilience drills pass
- [ ] observability dashboards show key metrics

### Stage 3
- [ ] EKS + RDS + Kafka fully provisioned via Terraform
- [ ] secrets and IAM configured correctly
- [ ] HTTPS and public access work
- [ ] CI/CD deployment pipeline works
- [ ] e2e script passes against public URL

## 11. Backlog (Later)
1. Split user-service from auth
2. Refresh tokens and OAuth2
3. Saga compensation patterns
4. Avro and schema registry
5. Redis-backed rate limiting
6. Row-Level Security
7. Schema-per-tenant enterprise model
8. OpenTelemetry and distributed tracing
9. Payment gateway integration
10. Webhooks and usage alerts
11. Contract testing with Pact or Spring Cloud Contract
12. Chaos testing

## 12. Execution Recommendation
The safest order is:
1. Infra
2. common
3. auth
4. device
5. usage
6. billing
7. notification
8. gateway
9. e2e
10. k8s
11. AWS

This matches the dependency graph and reduces rework. It also ensures each service is validated against a working event chain before the next layer is built.
