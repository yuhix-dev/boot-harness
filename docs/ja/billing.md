# 課金

BootHarnessは[Stripe](https://stripe.com)を使ったサブスクリプション課金に対応しています。2つのプラン（StarterとPRO）、StripeのホストCheckoutフロー、セルフサービスのカスタマーポータルをサポートします。

## 仕組み

1. ユーザーが `POST /api/v1/billing/checkout` を呼ぶ → Stripe CheckoutのURLを受け取る
2. ユーザーがStripeのホストページで支払いを完了
3. Stripeが `checkout.session.completed` Webhookを送信 → DBにサブスクリプションが保存される
4. ユーザーが `GET /api/v1/billing/subscription` でプランの状態を確認
5. ユーザーが `POST /api/v1/billing/portal` でプランの変更・解約を自己管理

## セットアップ

1. [Stripe](https://stripe.com) アカウントを作成
2. Stripeダッシュボードで2つのProductと定期課金Priceを作成（StarterとPRO）
3. Stripeダッシュボード → Settings → Billing → Customer Portal を有効化
4. Webhookエンドポイントを登録 → `https://your-domain.com/api/v1/billing/webhook`
   - 購読するイベント: `checkout.session.completed`, `customer.subscription.deleted`
5. 環境変数を設定:

```bash
STRIPE_SECRET_KEY=sk_...
STRIPE_WEBHOOK_SECRET=whsec_...
STRIPE_PRICE_ID_STARTER=price_...
STRIPE_PRICE_ID_PRO=price_...
```

## API

### サブスクリプション状態の取得

```
GET /api/v1/billing/subscription
Authorization: Bearer <accessToken>
```

レスポンス（サブスクリプションあり）:
```json
{
  "plan": "STARTER",
  "status": "ACTIVE",
  "currentPeriodEnd": "2026-04-21T00:00:00Z"
}
```

レスポンス（サブスクリプションなし）:
```json
{
  "plan": "FREE",
  "status": null,
  "currentPeriodEnd": null
}
```

### チェックアウト開始

```
POST /api/v1/billing/checkout
Authorization: Bearer <accessToken>
Content-Type: application/json

{
  "plan": "STARTER",
  "successUrl": "https://yourapp.com/billing/success",  // 省略可
  "cancelUrl": "https://yourapp.com/billing/cancel"     // 省略可
}
```

レスポンス:
```json
{ "checkoutUrl": "https://checkout.stripe.com/..." }
```

ユーザーを `checkoutUrl` にリダイレクトして支払いを完了させます。`successUrl`/`cancelUrl` を省略した場合は `{FRONTEND_BASE_URL}/billing/success` と `/billing/cancel` がデフォルトになります。

### カスタマーポータルを開く

```
POST /api/v1/billing/portal
Authorization: Bearer <accessToken>
```

レスポンス:
```json
{ "portalUrl": "https://billing.stripe.com/..." }
```

ユーザーを `portalUrl` にリダイレクトしてプランの変更・解約を自己管理させます。

## Webhook

`POST /api/v1/billing/webhook` は公開エンドポイント（JWT不要）です。Stripeが `Stripe-Signature` ヘッダーでリクエストを認証します。Webhookイベントは `stripe_events` テーブルに保存されたStripeイベントIDで冪等性を保証します。

| イベント | 処理 |
|---|---|
| `checkout.session.completed` | サブスクリプションレコードの作成・更新 |
| `customer.subscription.deleted` | サブスクリプションを `CANCELED` に更新 |

## 環境変数

| 変数 | 説明 |
|---|---|
| `STRIPE_SECRET_KEY` | StripeシークレットAPIキー |
| `STRIPE_WEBHOOK_SECRET` | Webhookの署名シークレット（Stripeダッシュボードから取得） |
| `STRIPE_PRICE_ID_STARTER` | StarterプランのStripe Price ID |
| `STRIPE_PRICE_ID_PRO` | PROプランのStripe Price ID |
