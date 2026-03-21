# Authentication

BootHarness ships with a complete authentication system built on Spring Security. This guide covers how it works and how to extend it.

## How It Works

Authentication uses two tokens:

| Token         | Storage  | Expiry  | Purpose                    |
|---------------|----------|---------|----------------------------|
| Access token  | Client   | 15 min  | Authenticates API requests |
| Refresh token | Database | 7 days  | Issues new access tokens   |

**Flow:**
1. Client sends `POST /api/v1/auth/login` with email + password (or completes OAuth2)
2. Server returns `{ accessToken, refreshToken }`
3. Client attaches `Authorization: Bearer <accessToken>` to every request
4. When the access token expires, client calls `POST /api/v1/auth/refresh` with the refresh token

## Database Schema

### `users` Table

```sql
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255),           -- null for OAuth2 users
    name       VARCHAR(255),
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

> **Why is `password` nullable?**
> OAuth2 users (Google, GitHub) never set a password. Only `LOCAL` users have a password.

### `oauth_identities` Table

OAuth2 provider links are stored separately to support **multiple providers per user** — e.g., a user can sign in with both Google and GitHub.

```sql
CREATE TABLE oauth_identities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider    VARCHAR(50)  NOT NULL,   -- GOOGLE | GITHUB
    provider_id VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_id)
);
```

This design allows one user to link multiple OAuth2 providers without schema changes.

## Supported Authentication Methods

| Method         | Endpoint                              | Notes                          |
|----------------|---------------------------------------|-------------------------------|
| Email/password | `POST /api/v1/auth/register` + login  | Password is BCrypt hashed      |
| Google OAuth2  | `GET /oauth2/authorization/google`    | Authorization Code + PKCE      |
| GitHub OAuth2  | `GET /oauth2/authorization/github`    | Authorization Code + PKCE      |

### Which method should I use?

Both methods are supported simultaneously and share the same user account if the email matches:

- **Email/password** — traditional login. Users register with an email and password, then log in with those credentials.
- **OAuth2 (Google / GitHub)** — social login. The user is redirected to the provider, then back to your frontend with tokens. No password required.

If a user registers via OAuth2 and later wants to also log in with a password, they can call `POST /api/v1/auth/password` while authenticated to set one.

If a user registered with a password and then logs in via OAuth2 with the same email, the OAuth2 identity is automatically linked to their existing account — no duplicate user is created.

## Password Management Endpoints

### Set password (authenticated)

`POST /api/v1/auth/password` — requires `Authorization: Bearer <accessToken>`

Sets or updates the password for the currently authenticated user. Useful for OAuth2 users who want to enable password-based login.

```json
{ "password": "newpassword123" }
```

Returns `204 No Content`.

### Request password reset

`POST /api/v1/auth/password/reset/request` — public

Sends a password reset email. Always returns `202 Accepted` regardless of whether the email exists (prevents email enumeration).

```json
{ "email": "user@example.com" }
```

The `PasswordResetRequestedEvent` is published — wire up an email listener to send the reset link. The token expires in **1 hour**.

### Confirm password reset

`POST /api/v1/auth/password/reset/confirm` — public

Validates the reset token and sets the new password. The token is single-use and deleted on success.

```json
{ "token": "<reset-token>", "newPassword": "newpassword123" }
```

Returns `204 No Content`. Throws `401` if the token is invalid or expired.

## Extending the `users` Table

The auth system only depends on `email`, `password`, and `role`. Any additional columns can be added via a new Flyway migration without touching the auth code.

**Example: Add a Stripe customer ID**

```sql
-- src/main/resources/db/migration/V4__add_stripe_customer_id.sql
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
```

**Example: Add a profile picture**

```sql
-- src/main/resources/db/migration/V4__add_user_profile_fields.sql
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
ALTER TABLE users ADD COLUMN bio        TEXT;
```

If the columns you need belong to a separate domain concern, consider a dedicated table instead:

```sql
-- src/main/resources/db/migration/V4__create_profiles.sql
CREATE TABLE profiles (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    avatar_url VARCHAR(512),
    bio        TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

## Role-Based Access Control (RBAC)

The default schema uses a single `role` column (`USER`, `ADMIN`). This covers most SaaS use cases. If you need multiple roles per user, add a `user_roles` junction table via migration — no changes to the existing `users` table required.

**Step 1: Add the migration**

```sql
-- src/main/resources/db/migration/V4__add_user_roles.sql
CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- Migrate existing roles
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM users;
```

**Step 2: Update `User` entity to load roles from the new table**

```java
@OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
private Set<UserRole> roles;

@Override
public Collection<? extends GrantedAuthority> getAuthorities() {
    return roles.stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.getRole()))
        .toList();
}
```

**Step 3: Protect endpoints with role checks**

```java
// In SecurityConfig
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

// Or on the method level
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminOnly() { ... }
```

## Environment Variables

| Variable               | Description                                                      |
|------------------------|------------------------------------------------------------------|
| `JWT_SECRET`           | Random secret, min 256-bit. **Required** — app will not start without this. |
| `GOOGLE_CLIENT_ID`     | Google OAuth2 client ID                                          |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 client secret                                      |
| `GITHUB_CLIENT_ID`     | GitHub OAuth2 client ID                                          |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth2 client secret                                      |

To generate a secure `JWT_SECRET`:
```bash
openssl rand -base64 64
```
