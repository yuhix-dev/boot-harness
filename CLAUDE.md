# CLAUDE.md — BootHarness Codebase Guide

This file helps AI coding assistants (Claude, Cursor, etc.) understand the BootHarness codebase quickly.

## What is this codebase?

BootHarness is a production-ready Spring Boot SaaS backend starter kit.
It provides authentication, billing, email, and a REST API foundation so you can focus on building your product.

**Backend API only** — No frontend is included. Connect any frontend (React, Vue, mobile, etc.) to the API.

## Tech Stack

| Layer | Choice |
|-------|--------|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| Auth | Spring Security + JWT + OAuth2 (Google, GitHub) |
| Database | Spring Data JPA + PostgreSQL |
| Migrations | Flyway |
| Billing | Stripe Java SDK |
| Email | Resend API |
| Container | Docker + docker-compose |

## Package Structure

Feature-based packaging under `com.bootharness`:

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

- **Java 21 features**: Use Records for DTOs, Pattern Matching where appropriate
- **Injection**: Constructor injection only. Never `@Autowired` on fields
- **DTOs**: All API request/response objects are DTOs. Never expose JPA entities directly
- **Logging**: SLF4J with structured key-value pairs. No string concatenation in log messages

### REST API

- Base path: `/api/v1/`
- Error response format:
  ```json
  { "error": "ERROR_CODE", "message": "Human-readable message", "details": {} }
  ```
- Input validation via Jakarta Validation annotations (`@Valid`, `@NotBlank`, etc.)

### Database

- Table names: `snake_case`, plural (e.g., `users`, `refresh_tokens`)
- Column names: `snake_case`
- Every table has: `id` (UUID), `created_at`, `updated_at`
- Flyway migration files: `src/main/resources/db/migration/V{n}__{description}.sql`
- JPA entity names: PascalCase singular (e.g., `User`, `RefreshToken`)
- **Never use** `ddl-auto: create` or `update` — Flyway manages all schema changes

## Security Patterns

- **Access tokens**: JWT, 15-minute expiry
- **Refresh tokens**: Stored in DB, 7-day expiry
- **Passwords**: BCrypt hashed
- **OAuth2**: Authorization Code flow with PKCE
- **CORS**: Configured via `CORS_ALLOWED_ORIGINS` env var

## Environment Variables

All configuration is environment-variable driven. See `application.yml` for the full list.

Key variables to set before running:

| Variable | Description |
|----------|-------------|
| `JWT_SECRET` | **FIXME**: Random secret, min 256-bit. App will not start without this. |
| `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | PostgreSQL connection |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth2 (optional) |
| `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET` | GitHub OAuth2 (optional) |
| `RESEND_API_KEY` | Resend email API key |
| `EMAIL_FROM` | **FIXME**: Your verified sender address |
| `STRIPE_SECRET_KEY` | Stripe secret key |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret |
| `STRIPE_PRICE_ID_STARTER` | **FIXME**: Your Stripe Price ID for the starter plan |
| `STRIPE_PRICE_ID_PRO` | **FIXME**: Your Stripe Price ID for the pro plan |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed frontend origins |

## Running Locally

```bash
# Start PostgreSQL only
docker-compose up db -d

# Run the app (requires JWT_SECRET to be set)
export JWT_SECRET=your-local-dev-secret-at-least-32-chars
./gradlew bootRun

# Or start everything with Docker
docker-compose up
```

## Customizing BootHarness

Common first steps after cloning:

1. **Rename the package**: Replace `com.bootharness` with your own package name throughout
2. **Set your environment variables**: Copy `.env.example` to `.env` and fill in values
3. **Add your domain tables**: Create new Flyway migration files in `db/migration/`
4. **Add your business logic**: Create new feature packages following the same structure
5. **Remove unused features**: Delete any packages you don't need (e.g., if not using Stripe)

## What's Out of Scope

Do not add these — they are intentionally excluded:

- Frontend frameworks
- Kafka or any message broker
- GraphQL / gRPC
- Kubernetes configs
- WebSocket
- Multi-tenancy
- Soft delete
- Feature flags
