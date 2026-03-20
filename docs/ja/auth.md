# 認証

BootHarnessにはSpring Securityをベースにした完全な認証システムが含まれています。このガイドでは仕組みの説明と拡張方法を解説します。

## 仕組み

認証には2種類のトークンを使用します。

| トークン       | 保存場所 | 有効期限 | 用途                             |
|---------------|---------|---------|----------------------------------|
| アクセストークン | クライアント | 15分 | APIリクエストの認証               |
| リフレッシュトークン | データベース | 7日 | 新しいアクセストークンの発行       |

**フロー：**
1. クライアントが `POST /api/v1/auth/login` にメール+パスワードを送信（またはOAuth2フローを完了）
2. サーバーが `{ accessToken, refreshToken }` を返す
3. クライアントはすべてのリクエストに `Authorization: Bearer <accessToken>` を付与
4. アクセストークンが期限切れになったら `POST /api/v1/auth/refresh` でリフレッシュ

## `users` テーブル

```sql
CREATE TABLE users (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255),           -- OAuth2ユーザーはnull
    name        VARCHAR(255),
    provider    VARCHAR(50)  NOT NULL DEFAULT 'LOCAL',  -- LOCAL | GOOGLE | GITHUB
    provider_id VARCHAR(255),           -- OAuth2の外部ID
    role        VARCHAR(50)  NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
```

> **`password` がnullableな理由**
> OAuth2ユーザー（Google、GitHub）はパスワードを持ちません。`LOCAL` ユーザーのみパスワードを設定します。

## 対応する認証方式

| 方式              | providerフィールド | 備考                          |
|------------------|------------------|-------------------------------|
| メール/パスワード  | `LOCAL`          | パスワードはBCryptでハッシュ化  |
| Google OAuth2    | `GOOGLE`         | Authorization Code + PKCE     |
| GitHub OAuth2    | `GITHUB`         | Authorization Code + PKCE     |

## `users` テーブルの拡張

認証システムが依存するのは `email`、`password`、`provider`、`provider_id`、`role` のみです。追加のカラムは新しいFlywayマイグレーションで追加できます。authのコードに変更は不要です。

**例：Stripe顧客IDを追加する**

```sql
-- src/main/resources/db/migration/V3__add_stripe_customer_id.sql
ALTER TABLE users ADD COLUMN stripe_customer_id VARCHAR(255);
```

**例：プロフィール画像を追加する**

```sql
-- src/main/resources/db/migration/V3__add_user_profile_fields.sql
ALTER TABLE users ADD COLUMN avatar_url  VARCHAR(512);
ALTER TABLE users ADD COLUMN bio         TEXT;
```

ドメイン固有の情報は別テーブルに分けた方がきれいな場合もあります。

```sql
-- src/main/resources/db/migration/V3__create_profiles.sql
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
-- src/main/resources/db/migration/V3__add_user_roles.sql
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

| 変数                   | 説明                                               |
|-----------------------|---------------------------------------------------|
| `JWT_SECRET`          | ランダムシークレット（最低256bit）。**必須** — 未設定だと起動しません |
| `GOOGLE_CLIENT_ID`    | Google OAuth2 クライアントID                        |
| `GOOGLE_CLIENT_SECRET`| Google OAuth2 クライアントシークレット               |
| `GITHUB_CLIENT_ID`    | GitHub OAuth2 クライアントID                        |
| `GITHUB_CLIENT_SECRET`| GitHub OAuth2 クライアントシークレット               |

`JWT_SECRET` の生成コマンド：
```bash
openssl rand -base64 64
```
