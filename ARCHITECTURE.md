# ARCHITECTURE.md — BootHarness System Architecture

## Overview

BootHarness is a Spring Boot 3.x SaaS backend starter kit. It is **backend API only** — no frontend is included. The API is consumed by any frontend (React, Next.js, mobile, etc.) via HTTP.

```
Frontend (any)
     │  HTTP/JSON
     ▼
BootHarness API  ─── PostgreSQL
     │
     ├── Stripe (billing)
     ├── Resend (email)
     └── Google / GitHub (OAuth2)
```

## Package Structure

All code lives under `com.bootharness`. Packaging is **feature-based**: every class for a feature lives in one directory.

```
com.bootharness
├── auth/        JWT issuance/validation, OAuth2, email+password login, password reset
├── billing/     Stripe subscriptions, webhooks, Customer Portal
├── email/       Resend API client, transactional email templates
├── user/        User entity, profile endpoints
├── api/         Global error handling (RFC 7807 ProblemDetail)
└── config/      Spring Security, CORS, AppProperties, Stripe, RestClient
```

Adding a new feature = add a new package. Removing a feature = delete its package. No cross-cutting wiring to update.

## Request Flow

```
HTTP Request
    │
    ▼
SecurityConfig.jwtAuthFilter()   — validates Bearer token, sets SecurityContext
    │
    ▼
Controller                        — validates request body (@Valid), calls Service
    │
    ▼
Service                           — business logic, transaction boundary (@Transactional)
    │
    ▼
Repository                        — Spring Data JPA, no custom SQL unless needed
    │
    ▼
PostgreSQL
```

For OAuth2 login:

```
Browser → /oauth2/authorization/{provider}
        → Provider (Google / GitHub)
        → /login/oauth2/code/{provider}
        → CustomOAuth2UserService / CustomOidcUserService
        → OAuth2AuthService.findOrCreateUser()
        → OAuth2AuthenticationSuccessHandler  — issues JWT pair, redirects to frontend
```

## Cross-Feature Communication

Features communicate via **Spring Application Events** — never by calling each other's services directly.

```
AuthService.register()
    └── publishes UserRegisteredEvent
            └── EmailService.onUserRegistered()  — sends welcome email

AuthService.requestPasswordReset()
    └── publishes PasswordResetRequestedEvent
            └── EmailService.onPasswordResetRequested()  — sends reset email

BillingService (Stripe webhook)
    └── publishes PaymentFailedEvent
            └── EmailService.onPaymentFailed()  — sends payment failure email
```

This keeps `auth` and `billing` decoupled from `email`. Swapping the email provider only touches `email/`.

## Authentication

Two authentication mechanisms coexist:

| Mechanism | Flow | Session |
|---|---|---|
| Email + password | POST /api/v1/auth/login → JWT pair | Stateless (no session) |
| OAuth2 (Google, GitHub) | Browser redirect flow → JWT pair | Short-lived session for OAuth2 state only |

`SecurityConfig.sessionCreationPolicy` is `IF_REQUIRED` so the OAuth2 state parameter can be stored in an HTTP session during the redirect flow. All API requests are authenticated via JWT — the session is not used after the callback.

**Token lifecycle:**
- Access token: short-lived JWT, validated on every request in `jwtAuthFilter`
- Refresh token: stored in `refresh_tokens` table, used to issue a new access token

## OAuth2 Provider Strategy Pattern

Adding a new OAuth2 provider requires:
1. Add registration to `application.yml` (Spring Security handles the redirect)
2. Implement `OAuth2ProviderStrategy` and annotate with `@Component`

No changes to `CustomOAuth2UserService` or `CustomOidcUserService` — they discover strategies via `List<OAuth2ProviderStrategy>` injection.

## Database Schema

Managed exclusively by Flyway. Never use `ddl-auto: create` or `update`.

```
users
├── id (UUID, PK)
├── email (unique)
├── name
├── password_hash (nullable — OAuth2 users have no password)
├── role
├── created_at
└── updated_at

oauth_identities
├── id (UUID, PK)
├── user_id (FK → users)
├── provider (GOOGLE | GITHUB)
├── provider_id
├── created_at
└── updated_at

refresh_tokens
├── id (UUID, PK)
├── user_id (FK → users)
├── token (unique)
├── expires_at
├── created_at
└── updated_at

password_reset_tokens
├── id (UUID, PK)
├── user_id (FK → users)
├── token_hash
├── expires_at
├── used_at (nullable)
├── created_at
└── updated_at

stripe_events      — idempotency log for Stripe webhooks
subscriptions      — active Stripe subscription per user
```

## Error Handling

All errors return RFC 7807 `ProblemDetail` (`application/problem+json`), handled centrally in `GlobalExceptionHandler`.

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "Email already in use"
}
```

Custom exceptions (`EmailAlreadyInUseException`, `InvalidCredentialsException`, `TokenException`) map to specific HTTP status codes in `GlobalExceptionHandler`.

## Configuration

All application config is typed via `AppProperties` (Spring Boot `@ConfigurationProperties`):

```
app.jwt.*            token expiry settings
app.cors.*           allowed origins
app.email.*          from address
app.stripe.*         price IDs, webhook secret
app.oauth2.*         frontend redirect base URL
```

Environment variables override `application.yml` values at runtime.

## Key Design Decisions

| Decision | Rationale |
|---|---|
| Feature-based packages | Self-contained features — easy to add, remove, or hand off |
| No Hexagonal / DDD | Optimized for a solo developer shipping fast. Layering within a package is sufficient at this scale |
| Spring Application Events | Decouples features without introducing a message broker |
| RFC 7807 ProblemDetail | Native to Spring Boot 3.x, standard error format across all clients |
| Flyway only | Prevents accidental schema drift in production |
| JWT stateless + session for OAuth2 | Balances API-first design with OAuth2 state requirements |
