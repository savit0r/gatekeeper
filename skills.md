# Gatekeeper --- Project Skills & Working Rules

## Purpose

Gatekeeper is a portfolio-grade, developer-focused API key management
and rate-limiting platform. Build it as a reliable, understandable
backend project that demonstrates practical Java/Spring Boot
engineering.

This file defines how an AI coding assistant or contributor should work
in this repository. Follow it before making code changes.

## Required Working Style

1.  **Mentor, don't dump.** Explain the goal and reasoning before
    substantial changes. Prefer small, reviewable steps over large
    rewrites.
2.  **One checkpoint at a time.** Give the user a concrete action,
    command, or small code change; then ask them to share the result
    before moving to the next risky step.
3.  **Don't assume files.** Inspect the current repository structure and
    file contents before editing. Never claim a command succeeded unless
    its output confirms it.
4.  **Explain trade-offs.** Briefly explain why a design is appropriate
    and what alternatives were rejected.
5.  **Preserve working code.** Avoid unrelated refactors and do not
    overwrite user work without explaining the change.
6.  **Prefer minimal diffs.** Change only what is needed for the current
    milestone.
7.  **Verify changes.** Run relevant tests/builds or provide the exact
    verification command. Report failures honestly.
8.  **Teach the fundamentals.** Explain unfamiliar Spring, Java, SQL,
    Redis, security, and testing concepts in clear language. Use
    pseudocode or a small example when helpful.
9.  **No fake completion.** Do not claim features, tests, security
    properties, performance, or deployment work that has not been
    implemented and verified.
10. **Keep the user in control.** Ask before destructive operations,
    dependency upgrades, deleting data, changing Git history, or
    publishing secrets/configuration.

## Scope Guardrails

### In scope for the MVP

-   A modular Spring Boot monolith.
-   PostgreSQL persistence and schema migrations.
-   Developer identity and dashboard authentication.
-   API key creation, listing, revocation, and rotation.
-   API keys stored as hashes; raw keys shown only at creation/rotation
    time.
-   Usage plans with request limits and time windows.
-   Redis-backed atomic rate limiting.
-   A protected sample API endpoint.
-   Rate-limit response headers and HTTP `429 Too Many Requests`.
-   Basic usage events and simple usage analytics.
-   Unit/integration tests, API documentation, health/metrics, Docker
    Compose, and CI.
-   Clear README, architecture diagram, setup instructions, and demo
    workflow.

### Explicitly out of scope unless the user approves a scope change

-   Microservices extraction or Spring Cloud Gateway deployment.
-   Billing, payments, subscriptions, or invoicing.
-   Multi-region/distributed Redis architecture.
-   Enterprise SSO, OAuth provider integrations, or complex
    organization/team permissions.
-   A polished production frontend or public SaaS launch.
-   Machine-learning-based anomaly detection.
-   Guaranteed lossless analytics/event delivery without a designed
    durable pipeline.
-   Kubernetes deployment before the local MVP, tests, and Docker
    workflow are solid.

When a new idea appears, classify it as **MVP**, **later enhancement**,
or **out of scope**. Do not silently expand the MVP.

## Engineering Standards

-   Use Java 21 and the repository's existing Spring Boot version. Do
    not upgrade the framework without a reason and approval.
-   Follow conventional Java naming, package-by-feature organization,
    and small cohesive classes.
-   Keep controllers thin. Put business rules in services and
    persistence logic in repositories.
-   Validate request inputs and return consistent, safe error responses.
-   Never commit passwords, tokens, private keys, real API keys, or
    local `.env` files.
-   Never store a raw API key in the database. Generate high-entropy
    secrets and store a cryptographic hash; use a prefix only for
    lookup/display.
-   Do not log credentials, authorization headers, or raw API keys.
-   Keep dashboard JWT/session authentication separate from consumer
    API-key authentication.
-   Rate limiting must be atomic under concurrent requests. Do not
    implement read-then-write counters as if they were atomic.
-   Define the Redis-outage behavior explicitly and test it. Do not
    silently imply the limiter remains effective when Redis is
    unavailable.
-   Prefer Flyway migrations for schema evolution. Once migrations
    define the schema, use Hibernate `ddl-auto=validate`, not `update`.
-   Use transactions where a business operation requires consistency.
-   Add tests for business rules, failure paths, and security-sensitive
    behavior.
-   Keep configuration externalizable. Document local development
    defaults as development-only.
-   Avoid adding dependencies unless the current feature needs them.

## Definition of Done

A change is complete only when:

-   The behavior matches the agreed scope.
-   The code is understandable and appropriately structured.
-   Relevant tests pass, or the exact reason they cannot run is stated.
-   No secrets or unrelated files are included.
-   Configuration and setup documentation are updated when necessary.
-   The user receives a short summary of what changed, why, and how to
    verify it.

## Git Safety

-   Check `git status` before editing/staging.
-   Inspect staged changes with `git diff --cached` before committing.
-   Stage specific files when practical; avoid `git add .` until the
    repository and ignore rules have been verified.
-   Never delete or rewrite unrelated repositories or Git metadata.
-   Do not push publicly until the staged diff has been checked for
    secrets and unrelated files.
