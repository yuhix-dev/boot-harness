# 認証

BootHarnessにはSpring Securityをベースにした完全な認証システムが含まれています。このガイドでは仕組みの説明と拡張方法を解説します。

## 仕組み

認証には2種類のトークンを使用します。

| トークン           | 保存場所       | 有効期限 | 用途                         |
|--------------------|--------------|---------|------------------------------|
| アクセストークン   | クライアント   | 15分    | APIリクエストの認証           |
| リフレッシュトークン | データベース  | 7日     | 新しいアクセストークンの発行   |

**フロー：**
1. クライアントが `POST /api/v1/auth/login` にメール+パスワードを送信（またはOAuth2フローを完了）
2. サーバーが `{ accessToken, refreshToken }` を返す
3. クライアントはすべてのリクエストに `Authorization: Bearer <accessToken>` を付与
4. アクセストークンが期限切れになったら `POST /api/v1/auth/refresh` でリフレッシュ

## データベーススキーマ

### `users` テーブル

```sql
CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) NOT NULL UNIQUE,
    password   VARCHAR(255),           -- OAuth2ユーザーはnull
    name       VARCHAR(255),
    role       VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

> **`password` がnullableな理由**
> OAuth2ユーザー（Google、GitHub）はパスワードを持ちません。`LOCAL` ユーザーのみパスワードを設定します。

### `oauth_identities` テーブル

OAuth2プロバイダーのリンク情報は別テーブルに分離することで、**1ユーザーが複数のプロバイダーを利用**できます。例えば、同じユーザーがGoogleとGitHubの両方でログインできます。

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

この設計により、スキーマ変更なしで複数のOAuth2プロバイダーを1ユーザーに紐付けられます。

## 対応する認証方式

| 方式              | エンドポイント                           | 備考                          |
|------------------|----------------------------------------|-------------------------------|
| メール/パスワード  | `POST /api/v1/auth/register` + login   | パスワードはBCryptでハッシュ化  |
| Google OAuth2    | `GET /oauth2/authorization/google`     | Authorization Code + PKCE     |
| GitHub OAuth2    | `GET /oauth2/authorization/github`     | Authorization Code + PKCE     |

### どちらを使うべきか

両方の認証方式は同時にサポートされており、メールアドレスが一致する場合は同じユーザーアカウントを共有します。

- **メール/パスワード** — 従来型のログイン。メールアドレスとパスワードで登録し、同じ情報でログインします。
- **OAuth2（Google / GitHub）** — ソーシャルログイン。プロバイダーにリダイレクトされ、認証後にトークンとともにフロントエンドに戻ります。パスワード不要。

OAuth2で登録したユーザーがパスワードログインも使いたい場合は、認証済み状態で `POST /api/v1/auth/password` を呼ぶとパスワードを設定できます。

パスワードで登録したユーザーが同じメールアドレスでOAuth2ログインした場合、OAuth2 identityが既存アカウントに自動的に紐付けられます（重複ユーザーは作成されません）。

## パスワード管理エンドポイント

### パスワード設定（認証必須）

`POST /api/v1/auth/password` — `Authorization: Bearer <accessToken>` が必要

現在認証されているユーザーのパスワードを設定または更新します。OAuth2ユーザーがパスワードログインも使えるようにする際に使用します。

```json
{ "password": "newpassword123" }
```

`204 No Content` を返します。

### パスワードリセット要求

`POST /api/v1/auth/password/reset/request` — 認証不要

パスワードリセットメールを送信します。メールアドレスが存在しない場合でも常に `202 Accepted` を返します（メールアドレスの列挙攻撃を防止）。

```json
{ "email": "user@example.com" }
```

`PasswordResetRequestedEvent` がパブリッシュされます — リセットリンクを送信するメールリスナーを実装してください。トークンの有効期限は **1時間** です。

### パスワードリセット確認

`POST /api/v1/auth/password/reset/confirm` — 認証不要

リセットトークンを検証して新しいパスワードを設定します。トークンは1回限り有効で、成功後に削除されます。

```json
{ "token": "<reset-token>", "newPassword": "newpassword123" }
```

`204 No Content` を返します。トークンが無効または期限切れの場合は `401` を返します。

## `users` テーブルの拡張

認証システムが依存するのは `email`、`password`、`role` のみです。追加のカラムは新しいFlywayマイグレーションで追加できます。authのコードに変更は不要です。

**例：Stripe顧客IDを追加する**

```sql
-- src/main/resources/db/migration/V4__add_stripe_customer_id.sql
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
```

**例：プロフィール画像を追加する**

```sql
-- src/main/resources/db/migration/V4__add_user_profile_fields.sql
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(512);
ALTER TABLE users ADD COLUMN bio        TEXT;
```

ドメイン固有の情報は別テーブルに分けた方がきれいな場合もあります。

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

## ロールベースアクセス制御（RBAC）

デフォルトのスキーマは単一の `role` カラム（`USER`、`ADMIN`）を使います。多くのSaaSユースケースではこれで十分です。ユーザーに複数のロールが必要な場合は、`user_roles` 中間テーブルをマイグレーションで追加します。既存の `users` テーブルの変更は不要です。

**ステップ1：マイグレーションを追加する**

```sql
-- src/main/resources/db/migration/V4__add_user_roles.sql
CREATE TABLE user_roles (
    user_id UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role    VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

-- 既存のroleを移行
INSERT INTO user_roles (user_id, role)
SELECT id, role FROM users;
```

**ステップ2：`User` エンティティを更新してロールを読み込む**

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

**ステップ3：エンドポイントをロールで保護する**

```java
// SecurityConfigで設定
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")

// またはメソッドレベルで設定
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<?> adminOnly() { ... }
```

## 環境変数

| 変数                    | 説明                                                     |
|------------------------|----------------------------------------------------------|
| `JWT_SECRET`           | ランダムシークレット（最低256bit）。**必須** — 未設定だと起動しません |
| `GOOGLE_CLIENT_ID`     | Google OAuth2 クライアントID                              |
| `GOOGLE_CLIENT_SECRET` | Google OAuth2 クライアントシークレット                     |
| `GITHUB_CLIENT_ID`     | GitHub OAuth2 クライアントID                              |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth2 クライアントシークレット                     |

`JWT_SECRET` の生成コマンド：
```bash
openssl rand -base64 64
```
