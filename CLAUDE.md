# CLAUDE.md — BootHarness Codebase Guide

> **Japanese?** A Japanese version is available at `CLAUDE.ja.md`.
> To use it, rename the files:
> ```bash
> mv CLAUDE.md CLAUDE.en.md && mv CLAUDE.ja.md CLAUDE.md
> ```

Spring Boot 3.x SaaS backend starter kit. **Backend API only** — no frontend included.
Rename `com.bootharness` to your own package before building your product.

## Package Structure

```
com.bootharness
├── auth/        # JWT issuance/validation, OAuth2, login/register/refresh endpoints
├── billing/     # Stripe subscriptions, webhooks, Customer Portal
├── email/       # Resend API client, email templates
├── user/        # User entity, profile management
├── api/         # Shared: error handling, validation, base response format
└── config/      # Security config, CORS, beans
```

## Code Conventions

- **Java 21**: Records for DTOs, Pattern Matching where appropriate, Virtual Threads enabled
- **Injection**: Constructor injection only — never `@Autowired` on fields
- **DTOs**: Never expose JPA entities directly in API responses
- **Logging**: SLF4J with structured key-value pairs — no string concatenation
- **No speculative work**: Only implement what is explicitly requested
- **Dead code**: When replacing an implementation, delete the old one entirely

## Architecture

- **Feature-based + Layered**: `Controller → Service → Repository` within each feature package
- **Spring Application Events** for cross-feature communication (e.g., `UserRegisteredEvent` → email)
  - Never call `EmailService` directly from `AuthService` — publish an event instead
- No Hexagonal/DDD — keep it simple and immediately readable

## REST API

- Base path: `/api/v1/`
- Error format: RFC 7807 `ProblemDetail` (Spring Boot 3.x native — `application/problem+json`)
- Validation: Jakarta annotations (`@Valid`, `@NotBlank`, etc.)

## Database

- Table names: `snake_case` plural — `users`, `refresh_tokens`
- Every table has: `id` (UUID), `created_at`, `updated_at`
- Migrations: `src/main/resources/db/migration/V{n}__{description}.sql`
- **Never** use `ddl-auto: create` or `update` — Flyway owns all schema changes

## Security

- Access tokens: JWT, 15-min expiry. Refresh tokens: DB-stored, 7-day expiry
- Passwords: BCrypt. OAuth2: Authorization Code + PKCE
- CORS: `CORS_ALLOWED_ORIGINS` env var

## FIXMEs — Required Before Launch

| Variable                  | Action                                              |
|---------------------------|-----------------------------------------------------|
| `JWT_SECRET`              | Generate a secure random secret (min 256-bit)       |
| `EMAIL_FROM`              | Set your verified Resend sender address             |
| `STRIPE_PRICE_ID_STARTER` | Create a Price in Stripe dashboard and paste the ID |
| `STRIPE_PRICE_ID_PRO`     | Same as above for Pro plan                          |

## Out of Scope

Frontend, Kafka, GraphQL, gRPC, Kubernetes, WebSocket, multi-tenancy, soft delete, feature flags.
