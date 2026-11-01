# Gatekeeper --- Project Context

> Keep this file current as the project evolves. It is the compact
> source of truth for project intent, architecture, current decisions,
> and next steps.

## 1. Product Summary

**Gatekeeper** is a developer-focused API management and rate-limiting
platform built as a portfolio project. A developer can manage API keys,
assign usage plans, and protect API endpoints with configurable request
limits. The system records usage so a developer can inspect basic
activity.

**Primary goal:** demonstrate backend engineering skills through a
small, well-tested, deployable product---not to build a full commercial
API gateway.

## 2. MVP Success Criteria

The MVP is considered complete when a developer can:

1.  Run the application and its dependencies locally using documented
    commands.
2.  Create/manage API keys, with the raw secret shown only once.
3.  Associate an API key with a usage plan.
4.  Call a protected sample endpoint using the key.
5.  Receive successful responses within the configured limit and HTTP
    `429` after exceeding it.
6.  See useful rate-limit headers.
7.  Revoke or rotate a key and have the old key rejected.
8.  Inspect basic usage data.
9.  Run automated tests and understand the API through OpenAPI
    documentation.
10. Build/run the app with a repeatable Docker-based development
    workflow.

## 3. Technology Baseline

-   Language: Java 21
-   Framework: the Spring Boot version already generated in this
    repository; inspect `pom.xml` before changing it
-   Build: Maven
-   API: Spring MVC / REST
-   Persistence: PostgreSQL + Spring Data JPA
-   Migrations: Flyway
-   Cache/counters: Redis
-   Validation: Jakarta Bean Validation
-   Security: Spring Security when the authentication milestone begins
-   Tests: JUnit 5, Mockito, Spring Boot Test; consider Testcontainers
    for integration tests
-   API docs: Springdoc OpenAPI
-   Health/metrics: Spring Boot Actuator and Micrometer
-   Local dependencies: Docker Compose
-   CI: GitHub Actions

Do not assume every dependency above has already been configured. Check
`pom.xml` and source files before acting.

## 4. Architecture Decisions

Start with a **modular monolith**, organized by feature/responsibility.
Do not split into microservices for the MVP.

Suggested feature areas:

-   `identity`: developer account and dashboard authentication
-   `apikey`: API key lifecycle and validation
-   `plans`: usage-plan definitions and assignment
-   `gateway`: API-key-protected request handling and rate limiting
-   `usage`: usage events and basic analytics
-   `observability`: health and metrics configuration

These are logical areas; adapt package names to the existing repository
instead of performing a large package restructure without approval.

### Data responsibilities

-   `Developer`: owns API keys and account identity.
-   `ApiKey`: belongs to a developer, references a usage plan, and
    tracks status/expiry/revocation.
-   `UsagePlan`: defines a request limit and time window.
-   `UsageEvent`: records an API request outcome and limited metadata.

Expected relationships:

-   One developer has many API keys.
-   One usage plan can be assigned to many API keys.
-   An API key can have many usage events.

Possible fields (refine before implementation):

-   `Developer`: `id`, `email`, `passwordHash`, `role`, `createdAt`
-   `ApiKey`: `id`, `developerId`, `planId`, `keyPrefix`, `keyHash`,
    `status`, `expiresAt`, `createdAt`, `revokedAt`
-   `UsagePlan`: `id`, `name`, `requestsPerWindow`, `windowSeconds`,
    `createdAt`
-   `UsageEvent`: `id`, `apiKeyId` (nullable if appropriate),
    `occurredAt`, `route`, `statusCode`, `latencyMs`, `decision`,
    `rejectionReason`

Treat these as design guidance, not proof that the entities or
migrations already exist.

## 5. Security Rules

-   Generate API keys using a cryptographically secure random generator
    with sufficient entropy.
-   Show the full raw API key only once when created or rotated.
-   Store only a one-way cryptographic hash of the key. Because
    generated keys have high entropy, a suitable cryptographic hash is
    appropriate; do not use a password hash as a substitute without a
    reason.
-   Use a short prefix to locate candidate keys, then compare hashes
    safely.
-   Never log raw keys, passwords, JWTs, or authorization headers.
-   Keep developer/dashboard authentication separate from API consumer
    authentication.
-   Return safe error messages that do not expose secrets or internal
    exception details.
-   Keep development credentials clearly marked as local-only. Use
    environment variables for overrides and ignore local `.env` files.
-   Do not claim production readiness from a successful local run alone.

## 6. Rate-Limiting Rules

-   Use Redis for counters and PostgreSQL for durable product data.
-   Counter updates must be atomic. Consider a Redis Lua script or
    another atomic Redis operation.
-   The first implementation may use a fixed window; document its
    boundary behavior and limitations.
-   Return HTTP `429 Too Many Requests` when the limit is exceeded.
-   Add clear rate-limit headers that accurately reflect the implemented
    algorithm.
-   Decide and test the behavior when Redis is unavailable (fail-open or
    fail-closed); document the trade-off.
-   Avoid unbounded key cardinality and avoid trusting arbitrary
    client-supplied identifiers as counter keys.
-   Do not claim exact analytics or lossless event capture unless the
    implementation guarantees it.

## 7. Local Development State

Known environment facts from setup:

-   macOS on Apple Silicon.
-   Java 21 (Temurin) is installed and the terminal was configured so
    Maven uses Java 21.
-   Maven 3.9.12 is installed.
-   Docker Desktop and Docker Compose are installed.
-   A Docker Compose file named `compose.yml` defines PostgreSQL 17:
    -   container: `gatekeeper-postgres`
    -   database: `gatekeeper`
    -   user: `gatekeeper`
    -   local development password: `gatekeeper_dev`
    -   host port: `5432`
    -   named volume: `gatekeeper_data`
-   A Homebrew PostgreSQL 14 service previously conflicted with the
    Docker port and was stopped.
-   The app successfully started after resolving a separate process
    conflict on port `8080`.
-   The project has a dedicated Git repository under the `gatekeeper`
    project directory. Do not modify or delete the unrelated
    `/Users/rohit/.git` repository.
-   A GitHub remote has not yet been confirmed.

Current application properties previously shared:

``` properties
spring.application.name=gatekeeper
spring.datasource.url=jdbc:postgresql://localhost:5432/gatekeeper
spring.datasource.username=gatekeeper
spring.datasource.password=gatekeeper_dev
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.open-in-view=false
```

These are the values the user shared earlier; inspect the actual current
file before assuming it has not changed. Before public publication,
externalize the password, check `.gitignore`, and inspect staged diffs
for secrets.

## 8. Current Workflow / Next Actions

The immediate workflow is to publish a clean initial repository to
GitHub safely.

1.  Inspect `git status` from the `gatekeeper` directory.
2.  Inspect `git remote -v`.
3.  Verify `.env` is ignored and `.gitignore` includes `.DS_Store`,
    `.env`, `.env.*`, `!.env.example`, and logs.
4.  Verify the database password/configuration is consistent for local
    development.
5.  Inspect tracked/untracked files and scan for credentials.
6.  Stage only intended project files.
7.  Review `git diff --cached`.
8.  Create the first commit only after review.
9.  Create a GitHub repository and add its remote; verify the exact
    remote URL before pushing.
10. Push `main`, then continue MVP implementation.

After GitHub setup, proceed incrementally through: - Confirm
dependencies and application startup. - Introduce a Flyway baseline
migration and switch Hibernate to `ddl-auto=validate`. - Implement and
test the core entities/relationships. - Implement API-key lifecycle. -
Implement protected endpoint and atomic Redis rate limiter. - Add usage
events/analytics. - Add integration tests, docs, health/metrics, CI, and
a demo.

Do not mark any of these steps complete until evidence confirms they are
complete.

## 9. Context-Window / AI Handoff Protocol

Use these rules when working with an AI assistant or coding agent that
has a limited context window:

### Before starting a task

1.  Read `skills.md` and this `context.md`.
2.  Inspect the relevant source files and `git status`.
3.  Identify the current milestone and the smallest next task.
4.  State assumptions and ask if an important decision is unclear.

### While working

-   Work on one coherent task at a time.
-   Prefer small diffs and explicit file lists.
-   Do not repeat the entire project history in every prompt; use this
    file as the compact baseline.
-   Do not invent files, test results, API routes, database columns, or
    completed features.
-   If repository state conflicts with this document, trust the actual
    repository and update this document after verifying the change.
-   Keep temporary notes short and factual.

### Before context runs low or a session ends

Update the **Session Handoff** section below with: - Current milestone -
What changed (specific files) - Commands/tests run and actual outcomes -
Known failures/blockers - Exact next action - Any important decisions
that are still open

### When resuming

Read the handoff, run `git status`, inspect the files relevant to the
next action, and verify claims before continuing. Do not blindly repeat
commands that may have already succeeded.

## 10. Session Handoff

**Last known milestone:** Observability & Prometheus Metrics (Completed). Production-grade Prometheus metrics scraping (`/actuator/prometheus`) and Micrometer telemetry across Gatekeeper!

**Last known confirmed state:**
- Dedicated Git repository up to date with `origin/main` (`d31d62c`).
- Docker containers (`gatekeeper-postgres` and `gatekeeper-redis`) are running and healthy.
- Continuous Integration:
  - `.github/workflows/ci.yml`: GitHub Actions pipeline with PostgreSQL 17 and Redis 7 service containers running `chmod +x mvnw && ./mvnw clean verify --batch-mode`.
  - Build status badge linked in `README.md`.
- Observability & Prometheus Metrics:
  - Added `io.micrometer:micrometer-registry-prometheus` to `pom.xml`.
  - Exposed Actuator endpoints (`health,info,prometheus,metrics`) with `management.prometheus.metrics.export.enabled=true` in `application.properties`.
  - `GatekeeperMetrics`: central component registering custom domain meters:
    - `gatekeeper.ratelimit.requests` (`status: allowed|rejected`, `plan: <planName>`)
    - `gatekeeper.keycache.lookups` (`result: hit|miss`)
    - `gatekeeper.key.lifecycle` (`action: created|revoked|rotated`)
    - `gatekeeper.auth.logins` (`result: success|failure`)
    - `gatekeeper.gateway.latency` (nanosecond-precision timer)
  - Seamlessly instrumented `ApiKeyRateLimitInterceptor`, `ApiKeyService`, and `AuthService` using non-invasive `ObjectProvider<GatekeeperMetrics>`.
  - `GatekeeperMetricsTest`: comprehensive unit test suite verifying meter tag consistency and registrations.
  - `PrometheusEndpointIntegrationTest`: full MockMvc integration test verifying `/actuator/health` and `/actuator/prometheus` scraping.
- Developer JWT Authentication:
  - `JwtService`: zero-dependency HMAC-SHA256 JWT creation, signing, parsing, constant-time signature verification, and expiration enforcement.
  - `AuthService` & `AuthController`: `POST /api/v1/auth/login` verifies developer credentials and issues signed Bearer tokens.
  - `DeveloperSecurityResolver`: resolves authenticated developer ID from `Authorization: Bearer <jwt>` with backward-compatible `X-Developer-Id` header fallback.
  - Secured dashboard controllers (`/api/v1/developers/me`, `/api/v1/keys`, `/api/v1/analytics`).
- Redis API Key Caching:
  - `CachedApiKey`: compact immutable record holding active key and plan limits.
  - `RedisApiKeyCache`: `StringRedisTemplate` Redis Hash (`gatekeeper:keycache:<hash>`) with configurable TTL (`gatekeeper.cache.api-key-ttl-seconds=600`) and graceful fail-open resilience.
  - `ApiKeyService`: checks Redis cache first; on cache miss, queries DB and populates cache; on key revocation or rotation, immediately evicts from Redis.
  - `ApiKeyCacheIntegrationTest`: end-to-end integration test validating cache population on first call, cache hit on second call, and eviction on revocation.
- Flyway migrations:
  - `V1__init_schema.sql`: tables for `developers`, `usage_plans`, `api_keys`, `usage_events`, indexes.
  - `V2__seed_default_plans.sql`: seeds deterministic default plans (`Starter`, `Pro`, `Enterprise`).
- Identity & Plans:
  - `DeveloperService` & `DeveloperController`: developer registration with SHA-256 hashed passwords and profile retrieval.
  - `UsagePlanController`: lists available usage tiers.
- API Key Lifecycle:
  - 256-bit entropy key generation (`gk_live_<prefix>_<secret>`), prefix lookup, SHA-256 hashing, constant-time validation (`MessageDigest.isEqual`), creation (shown once), rotation, and revocation.
- Atomic Rate Limiting & Gateway:
  - `RedisRateLimiter`: single atomic Lua script (`INCR`, `EXPIRE`, `TTL`) with fail-closed/fail-open policies.
  - `ApiKeyRateLimitInterceptor`: verifies keys, attaches `X-RateLimit-*` headers, returns `HTTP 429` with `Retry-After`.
  - `ProtectedSampleController`: `GET /api/v1/protected/hello`.
- Usage Tracking & Analytics:
  - `UsageEventService`: asynchronous non-blocking event recording (`ALLOWED`/`REJECTED`, latency, rejection reason).
  - `UsageAnalyticsController`: summary statistics and recent audit event log.
- OpenAPI & Documentation:
  - Springdoc OpenAPI (Swagger UI) configured at `/swagger-ui.html`.
  - Comprehensive `README.md` with Mermaid architecture diagram, quickstart guide, and cURL walkthrough.
- Full test suite: 64/64 tests passing (0 failures, 0 errors across unit, slice, and integration tests).

**Next action:**
1. Stage and commit Observability & Prometheus Metrics changes.
2. Push to `origin/main` so GitHub Actions automatically executes the workflow.
3. Proceed with remaining stages:
   - Stage 2: Production Containerization (`Dockerfile` multi-stage build + production profile).
   - Stage 3: High-Concurrency Virtual Thread / k6 Stress Benchmark.

**Update this handoff after each meaningful milestone.**






