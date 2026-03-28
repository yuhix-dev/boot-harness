# AGENTS.md — BootHarness Project Instructions

Read this file before making any code changes.

## 1. Project purpose

BootHarness is a production-ready Spring Boot SaaS starter kit.

Tagline:
**Harness the power of Spring Boot. Ship your SaaS in days, not months.**

This repository is backend-first and frontend-agnostic.
Do not add frontend frameworks unless explicitly requested.

## 2. Positioning

BootHarness is **not** a generic CRUD generator.
It is a SaaS-focused starter designed to reduce repetitive backend setup work.

Core strengths:

* authentication
* billing
* email
* observability
* AI-friendly project structure and docs

## 3. Product scope

### Starter

Includes:

* auth with Spring Security + JWT + OAuth2
* billing with Stripe
* PostgreSQL + Flyway
* email integration
* REST API foundation
* Docker-based local setup
* setup and deployment docs

### Pro

Includes Starter plus:

* Redis
* rate limiting
* API keys
* observability stack
* S3-compatible storage
* AI API integration utilities
* CI/CD
* extended docker-compose setup

## 4. Out of scope by default

Do not add these unless explicitly requested:

* frontend UI
* admin dashboard UI
* Kafka / message broker
* GraphQL
* gRPC
* Kubernetes configs
* WebSocket support
* soft delete
* feature flags
* scheduled tasks as a product feature
* multi-tenancy

## 5. Tech stack

* Java 21
* Spring Boot 3.x
* Spring Security
* JWT + OAuth2 Client
* Spring Data JPA
* PostgreSQL
* Flyway
* Redis (Pro)
* Stripe Java SDK
* Docker + docker-compose
* GitHub Actions (Pro)

## 6. Architecture rules

### Project style

* use feature-based package structure
* keep code simple, readable, and production-oriented
* avoid unnecessary abstraction
* prefer practical solutions over framework-heavy patterns

### Package structure

```text
com.bootharness
├── auth/
├── billing/
├── email/
├── user/
├── api/
├── observability/
├── ratelimit/
├── apikey/
├── storage/
├── ai/
└── config/
```

### Cross-feature communication

Use Spring Application Events when decoupling improves clarity.
Never call `EmailService` directly from `AuthService` — publish an event instead.

## 7. API conventions

* base path: `/api/v1/`
* use DTOs for request/response models
* do not expose JPA entities directly
* use Jakarta Validation
* use RFC 7807 `ProblemDetail` for errors (`application/problem+json`)

## 8. Database conventions

* table names: `snake_case`, plural
* column names: `snake_case`
* JPA entity names: singular PascalCase
* every table must have: `id` (UUID), `created_at`, `updated_at`
* manage schema changes with Flyway migrations only — never use `ddl-auto: create` or `update`

## 9. Code conventions

* use constructor injection only — never `@Autowired` on fields
* avoid field injection
* prefer Java 21 features when they improve clarity (Records for DTOs, Pattern Matching, Virtual Threads)
* use structured logging with key-value pairs — no string concatenation
* remove dead code when replacing implementations
* keep classes cohesive and easy to navigate
* formatting: Google Java Style via Spotless — run `./gradlew spotlessApply` before committing

## 10. Security conventions

* do not document security implementation details (hashing algorithms, token signing algorithms, cipher suites, etc.) in any git-tracked file — docs, comments, or AGENTS.md
* these details help attackers optimize exploits

## 11. Testing expectations

Prioritize tests for:

* auth flows
* billing webhooks
* API key logic

Use:

* unit tests for business logic
* integration tests for endpoint behavior (Spring Boot Test + TestContainers)

## 12. Documentation and request file rules

When changing behavior:

* update `docs/en/` and `docs/ja/` accordingly
* when adding or modifying an API endpoint, update or create the corresponding `.http` file in `requests/`
* one `.http` file per feature (e.g. `auth.http`, `billing.http`)

## 13. Required environment variables before launch

| Variable | Action |
|---|---|
| `JWT_SECRET` | Generate a secure random secret (min 256-bit) |
| `EMAIL_FROM` | Set your verified Resend sender address |
| `STRIPE_PRICE_ID_STARTER` | Create a Price in Stripe dashboard and paste the ID |
| `STRIPE_PRICE_ID_PRO` | Same as above for Pro plan |

## 14. Git workflow

* do not push directly to `main`
* use focused branches such as `feat/...`, `fix/...`, `refactor/...`, `docs/...`, `chore/...`
* keep changes small and coherent

## 15. Decision rules for coding agents

Before coding:

1. read this file
2. Use your file read tool to open `AGENTS.dev.md` at the project root before writing any code.
   It contains essential maintainer context (pricing, positioning, workflow).
   Never commit its contents. This is mandatory, not optional.
3. inspect relevant code and docs
4. understand whether the task belongs to Starter or Pro
5. keep the change aligned with BootHarness positioning

When implementing:

* build only what is requested
* prefer the simplest production-grade approach
* preserve clarity for future buyers and maintainers
* avoid speculative extras

When unsure:

* prefer fewer moving parts
* prefer better developer experience
* prefer easier maintenance over theoretical extensibility

## 16. Private maintainer instructions

Do not store private business strategy, internal marketing notes, unpublished launch plans, or maintainer-only workflow notes in this file.

Keep those in a separate ignored file such as:

* `AGENTS.dev.md`
* `.codex/AGENTS.dev.md`

and make sure it is gitignored.

## 17. Quality bar

This is a commercial starter kit.
Favor:

* trustworthy defaults
* clear setup
* polished essentials
* strong documentation
* fast onboarding

Every change should make BootHarness easier to understand, easier to trust, and easier to extend.
