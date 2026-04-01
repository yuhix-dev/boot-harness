# BootHarness

BootHarness is a Spring Boot backend application.

## Stack
- Java 21
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Stripe
- OAuth2 Client

## Prerequisites
- Java 21
- PostgreSQL
- A populated `.env` file based on `.env.example`

## Setup
1. Copy `.env.example` to `.env`.
2. Fill in the required environment variables.
3. Start PostgreSQL.
4. Run the application.

```bash
cp .env.example .env
./gradlew bootRun
```

If `.env` exists, `bootRun` loads it automatically at startup.

## Environment Variables
Common variables used by this project:
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `RESEND_API_KEY`, `EMAIL_FROM`
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`

## Common Commands
```bash
./gradlew bootRun
./gradlew test
./gradlew spotlessCheck
./gradlew spotlessApply
```

## Docs and References
- English docs: `docs/en/`
- Japanese docs: `docs/ja/`
- Supplemental notes: `HELP.md`
- HTTP request samples and project notes: `requests/`

## Development Notes
- `Dockerfile`, `docker-compose.yml`, and `docker-compose.full.yml` are available for local container-based workflows.
- Keep the README aligned with `README.md`, `HELP.md`, and the `docs/` directory when setup details change.
