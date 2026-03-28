# デプロイ

## 環境変数

アプリを起動する前に、必要な環境変数をすべて設定してください。ローカル開発では `.env.example` を `.env` にコピーして使います。

| 変数                      | 必須     | 説明                                                     |
|---------------------------|----------|----------------------------------------------------------|
| `JWT_SECRET`              | Yes      | ランダムなシークレット（最低256ビット）。`openssl rand -base64 64` で生成 |
| `DB_URL`                  | Yes      | JDBC URL — 例: `jdbc:postgresql://localhost:5432/bootharness` |
| `DB_USERNAME`             | Yes      | データベースのユーザー名                                  |
| `DB_PASSWORD`             | Yes      | データベースのパスワード                                  |
| `RESEND_API_KEY`          | Yes      | Resend の API キー                                        |
| `EMAIL_FROM`              | Yes      | 認証済みの送信元アドレス — 例: `noreply@yourdomain.com`  |
| `CORS_ALLOWED_ORIGINS`    | Yes      | 許可するオリジンのカンマ区切りリスト — 例: `https://yourapp.com` |
| `STRIPE_SECRET_KEY`       | Yes      | Stripe のシークレット API キー (`sk_live_...`)            |
| `STRIPE_WEBHOOK_SECRET`   | Yes      | Stripe ダッシュボードの Webhook 署名シークレット (`whsec_...`) |
| `STRIPE_PRICE_ID_STARTER` | Yes      | Starter プランの Stripe Price ID                         |
| `STRIPE_PRICE_ID_PRO`     | Yes      | PRO プランの Stripe Price ID                             |
| `GOOGLE_CLIENT_ID`        | OAuth2   | Google OAuth2 クライアント ID                             |
| `GOOGLE_CLIENT_SECRET`    | OAuth2   | Google OAuth2 クライアントシークレット                    |
| `GITHUB_CLIENT_ID`        | OAuth2   | GitHub OAuth2 クライアント ID                             |
| `GITHUB_CLIENT_SECRET`    | OAuth2   | GitHub OAuth2 クライアントシークレット                    |
| `FRONTEND_BASE_URL`       | OAuth2   | OAuth2 リダイレクト先のフロントエンド URL                 |

---

## Docker

### イメージのビルド

```bash
./gradlew bootJar
docker build -t boot-harness .
```

### Docker Compose での起動

`docker-compose.yml` が含まれており、PostgreSQL とともにローカルで起動できます。

```bash
docker-compose up
```

本番環境では、アプリコンテナのみを起動し、外部のマネージドデータベースに向けます：

```bash
docker run --env-file .env -p 8080:8080 boot-harness
```

---

## Railway

[Railway](https://railway.app) は手軽にデプロイできる推奨プラットフォームです。

1. Railway で新規プロジェクトを作成
2. **PostgreSQL** サービスを追加 — Railway が `DATABASE_URL` を自動設定
3. GitHub リポジトリを接続
4. **Variables** タブで必要な環境変数をすべて設定
5. `Dockerfile` を検出して `main` へのプッシュ時に自動デプロイ

**DB_URL の設定：** Railway のリファレンス変数を使うと便利です：

```
DB_URL=jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}
DB_USERNAME=${{Postgres.PGUSER}}
DB_PASSWORD=${{Postgres.PGPASSWORD}}
```

---

## Render

1. [Render](https://render.com) で新規 **Web Service** を作成
2. GitHub リポジトリを接続
3. **Build Command** を設定: `./gradlew bootJar`
4. **Start Command** を設定: `java -jar build/libs/bootharness-0.0.1-SNAPSHOT.jar`
5. **PostgreSQL** データベースサービスを追加して Web Service にリンク
6. **Environment** タブで必要な環境変数をすべて設定

---

## データベースマイグレーション

Flyway は起動時に自動でマイグレーションを実行するため、手動のマイグレーション手順は不要です。

起動が成功したか確認するには：

```bash
curl https://your-domain.com/actuator/health
# {"status":"UP"}
```

---

## Stripe Webhook の設定

デプロイ後、Stripe ダッシュボードで Webhook エンドポイントを登録します：

1. **Stripe Dashboard → Developers → Webhooks** を開く
2. **Add endpoint** をクリック
3. URL に `https://your-domain.com/api/v1/billing/webhook` を設定
4. イベントを選択: `checkout.session.completed`, `customer.subscription.updated`, `customer.subscription.deleted`, `invoice.payment_failed`
5. **Signing secret** をコピーして `STRIPE_WEBHOOK_SECRET` に設定
