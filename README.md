# BootHarness

Spring Boot ベースのバックエンドアプリケーションです。

## 技術スタック
- Java 21
- Spring Boot 3.5
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Stripe
- OAuth2 Client

## セットアップ
1. `.env.example` をコピーして `.env` を作成する
2. 必要な環境変数を設定する
3. PostgreSQL を起動する

```bash
cp .env.example .env
./gradlew bootRun
```

`.env` が存在する場合、`bootRun` はその内容を読み込んで起動します。

## 主な環境変数
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
- `JWT_SECRET`
- `CORS_ALLOWED_ORIGINS`
- `RESEND_API_KEY`, `EMAIL_FROM`
- `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`
- `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`

## よく使うコマンド
```bash
./gradlew bootRun
./gradlew test
./gradlew spotlessCheck
./gradlew spotlessApply
```

## ドキュメント
- 英語ドキュメント: `docs/en/`
- 日本語ドキュメント: `docs/ja/`
- 補助情報: `HELP.md`
