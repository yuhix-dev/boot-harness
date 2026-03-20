# CLAUDE.md — BootHarness コードガイド

> **English?** 英語版は `CLAUDE.en.md` として保存されています。
> 切り替えるには:
> ```bash
> mv CLAUDE.md CLAUDE.ja.md && mv CLAUDE.en.md CLAUDE.md
> ```

Spring Boot 3.x SaaS バックエンドスターターキット。**バックエンドAPIのみ** — フロントエンドは含まれません。
プロダクト開発を始める前に `com.bootharness` を自分のパッケージ名に変更してください。

## パッケージ構成

```
com.bootharness
├── auth/        # JWT発行・検証、OAuth2、ログイン/登録/リフレッシュ エンドポイント
├── billing/     # Stripeサブスクリプション、Webhook、カスタマーポータル
├── email/       # Resend APIクライアント、メールテンプレート
├── user/        # Userエンティティ、プロフィール管理
├── api/         # 共通: エラーハンドリング、バリデーション、レスポンス形式
└── config/      # セキュリティ設定、CORS、Bean定義
```

## コード規約

- **Java 21**: DTOにはRecord、適切な箇所にはPattern Matching、Virtual Threads有効
- **DI**: コンストラクタインジェクションのみ — フィールドへの `@Autowired` は禁止
- **DTO**: JPA エンティティをAPIレスポンスに直接使用しない
- **ログ**: SLF4J + 構造化キーバリュー形式 — 文字列結合は使わない
- **投機的実装禁止**: 明示的に依頼されたことだけ実装する
- **デッドコード**: 実装を置き換えたら古いコードは必ず削除する

## アーキテクチャ

- **フィーチャーベース + レイヤード**: 各パッケージ内で `Controller → Service → Repository`
- **Spring Application Events** でフィーチャー間通信（例: `UserRegisteredEvent` → メール送信）
  - `AuthService` から `EmailService` を直接呼ばずにイベントを発行する
- Hexagonal/DDD は使わない — シンプルで即読みできる構成を優先

## REST API

- ベースパス: `/api/v1/`
- エラー形式: RFC 7807 `ProblemDetail`（Spring Boot 3.x ネイティブ — `application/problem+json`）
- バリデーション: Jakarta アノテーション（`@Valid`, `@NotBlank` など）

## データベース

- テーブル名: `snake_case` 複数形 — `users`, `refresh_tokens`
- 全テーブルに必須: `id`（UUID）、`created_at`、`updated_at`
- マイグレーション: `src/main/resources/db/migration/V{n}__{説明}.sql`
- `ddl-auto: create` / `update` は**絶対に使わない** — スキーマ変更はFlywayが管理

## セキュリティ

- アクセストークン: JWT、有効期限15分。リフレッシュトークン: DB保存、有効期限7日
- パスワード: BCryptハッシュ。OAuth2: Authorization Code + PKCE
- CORS: `CORS_ALLOWED_ORIGINS` 環境変数で設定

## ローンチ前に必ず設定する環境変数

| 変数 | 対応内容 |
|------|---------|
| `JWT_SECRET` | 安全なランダムシークレットを生成（最低256bit） |
| `EMAIL_FROM` | Resendで認証済みの送信元アドレスを設定 |
| `STRIPE_PRICE_ID_STARTER` | Stripeダッシュボードで作成したPrice IDを設定 |
| `STRIPE_PRICE_ID_PRO` | Proプラン用も同様に設定 |

## スコープ外

フロントエンド、Kafka、GraphQL、gRPC、Kubernetes、WebSocket、マルチテナント、論理削除、フィーチャーフラグ。
