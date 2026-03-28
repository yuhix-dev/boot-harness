# Deployment

## Environment Variables

All required environment variables must be set before starting the app. Copy `.env.example` to `.env` for local development.

| Variable                  | Required | Description                                              |
|---------------------------|----------|----------------------------------------------------------|
| `JWT_SECRET`              | Yes      | Random secret, min 256-bit. Generate with `openssl rand -base64 64` |
| `DB_URL`                  | Yes      | JDBC URL — e.g. `jdbc:postgresql://localhost:5432/bootharness` |
| `DB_USERNAME`             | Yes      | Database username                                        |
| `DB_PASSWORD`             | Yes      | Database password                                        |
| `RESEND_API_KEY`          | Yes      | Resend API key                                           |
| `EMAIL_FROM`              | Yes      | Verified sender address — e.g. `noreply@yourdomain.com` |
| `CORS_ALLOWED_ORIGINS`    | Yes      | Comma-separated list of allowed origins — e.g. `https://yourapp.com` |
| `STRIPE_SECRET_KEY`       | Yes      | Stripe secret API key (`sk_live_...`)                    |
| `STRIPE_WEBHOOK_SECRET`   | Yes      | Webhook signing secret from Stripe dashboard (`whsec_...`) |
| `STRIPE_PRICE_ID_STARTER` | Yes      | Stripe Price ID for the Starter plan                     |
| `STRIPE_PRICE_ID_PRO`     | Yes      | Stripe Price ID for the PRO plan                         |
| `GOOGLE_CLIENT_ID`        | OAuth2   | Google OAuth2 client ID                                  |
| `GOOGLE_CLIENT_SECRET`    | OAuth2   | Google OAuth2 client secret                              |
| `GITHUB_CLIENT_ID`        | OAuth2   | GitHub OAuth2 client ID                                  |
| `GITHUB_CLIENT_SECRET`    | OAuth2   | GitHub OAuth2 client secret                              |
| `FRONTEND_BASE_URL`       | OAuth2   | Frontend base URL for OAuth2 redirects                   |

---

## Docker

### Build the image

```bash
./gradlew bootJar
docker build -t boot-harness .
```

### Run with Docker Compose

A `docker-compose.yml` is included for local development with a bundled PostgreSQL database.

```bash
docker-compose up
```

For production, run only the app container and point it at an external managed database:

```bash
docker run --env-file .env -p 8080:8080 boot-harness
```

---

## Railway

[Railway](https://railway.app) is the recommended platform for quick deploys.

1. Create a new project in Railway
2. Add a **PostgreSQL** service — Railway sets `DATABASE_URL` automatically
3. Connect your GitHub repository
4. Set all required environment variables in the **Variables** tab
5. Railway detects the `Dockerfile` and deploys automatically on push to `main`

**DB_URL mapping:** Railway provides `DATABASE_URL` in the format `postgresql://user:pass@host:port/db`.
Rewrite it to JDBC format for Spring Boot, or use Railway's reference variables:

```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
```

---

## Render

1. Create a new **Web Service** on [Render](https://render.com)
2. Connect your GitHub repository
3. Set **Build Command**: `./gradlew bootJar`
4. Set **Start Command**: `java -jar build/libs/bootharness-0.0.1-SNAPSHOT.jar`
5. Add a **PostgreSQL** database service and link it to the web service
6. Set all required environment variables in the **Environment** tab

---

## Database Migrations

Flyway runs automatically on startup — no manual migration step needed.

Verify the app started successfully:

```bash
curl https://your-domain.com/actuator/health
# {"status":"UP"}
```

---

## Stripe Webhook Setup

After deploying, register the webhook endpoint in the Stripe dashboard:

1. Go to **Stripe Dashboard → Developers → Webhooks**
2. Click **Add endpoint**
3. Set the URL to `https://your-domain.com/api/v1/billing/webhook`
4. Select events: `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_failed`
5. Copy the **Signing secret** and set it as `STRIPE_WEBHOOK_SECRET`
