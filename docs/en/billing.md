# Billing

BootHarness integrates with [Stripe](https://stripe.com) for subscription billing. It supports two plans (Starter and PRO), a hosted Checkout flow, and the Stripe Customer Portal for self-service plan management.

## How It Works

1. User calls `POST /api/v1/billing/checkout` → receives a Stripe Checkout URL
2. User completes payment on Stripe's hosted page
3. Stripe sends a `checkout.session.completed` webhook → subscription is saved in DB
4. User calls `GET /api/v1/billing/subscription` to check their plan status
5. User calls `POST /api/v1/billing/portal` to manage or cancel their subscription

## Setup

1. Create a [Stripe](https://stripe.com) account
2. Create two Products with recurring Prices (Starter and PRO) in the Stripe dashboard
3. Enable the Customer Portal in Stripe dashboard → Settings → Billing → Customer Portal
4. Create a webhook endpoint pointing to `https://your-domain.com/api/v1/billing/webhook`
   - Events to subscribe: `checkout.session.completed`, `customer.subscription.deleted`
5. Set environment variables:

```bash
STRIPE_SECRET_KEY=sk_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_ID_STARTER=price_...
STRIPE_PRICE_ID_PRO=price_...
```

## API

### Get subscription status

```
GET /api/v1/billing/subscription
Authorization: Bearer <accessToken>
```

Response (subscribed):
```json
{
  "plan": "STARTER",
  "status": "ACTIVE",
  "currentPeriodEnd": "2026-04-21T00:00:00Z"
}
```

Response (no subscription):
```json
{
  "plan": "FREE",
  "status": null,
  "currentPeriodEnd": null
}
```

### Start checkout

```
POST /api/v1/billing/checkout
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "plan": "STARTER",
  "successUrl": "https://yourapp.com/billing/success",  // optional
  "cancelUrl": "https://yourapp.com/billing/cancel"     // optional
}
```

Response:
```json
{ "checkoutUrl": "https://checkout.stripe.com/..." }
```

Redirect the user to `checkoutUrl` to complete payment. If `successUrl`/`cancelUrl` are omitted, defaults to `{FRONTEND_BASE_URL}/billing/success` and `/billing/cancel`.

### Open Customer Portal

```
POST /api/v1/billing/portal
Authorization: Bearer <accessToken>
```

Response:
```json
{ "portalUrl": "https://billing.stripe.com/..." }
```

Redirect the user to `portalUrl` to manage or cancel their subscription.

## Webhook

`POST /api/v1/billing/webhook` is public (no JWT). Stripe authenticates requests via the `Stripe-Signature` header. Webhook events are deduplicated by Stripe event ID stored in the `stripe_events` table.

| Event | Action |
|---|---|
| `checkout.session.completed` | Create or update subscription record |
| `customer.subscription.deleted` | Mark subscription as `CANCELED` |

## Environment Variables

| Variable | Description |
|---|---|
| `STRIPE_SECRET_KEY` | Stripe secret API key |
| `STRIPE_WEBHOOK_SECRET` | Webhook signing secret (from Stripe dashboard) |
| `STRIPE_PRICE_ID_STARTER` | Stripe Price ID for the Starter plan |
| `STRIPE_PRICE_ID_PRO` | Stripe Price ID for the PRO plan |
