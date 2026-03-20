# はじめに

## 必要な環境

- Java 21以上
- Docker & Docker Compose
- IntelliJ IDEA（推奨）

## 1. クローンと設定

```bash
git clone <リポジトリURL>
cd your-project
cp .env.example .env   # 値を埋める
```

起動前に最低限これだけ `.env` に設定してください：

```bash
JWT_SECRET=<openssl rand -base64 64 で生成>
DB_PASSWORD=ローカル用のパスワード
```

## 2. データベースを起動

```bash
docker-compose up db -d
```

## 3. アプリを起動

```bash
./gradlew bootRun
```

起動時にFlywayマイグレーションが自動実行されます。APIは `http://localhost:8080` で利用できます。

## 4. 動作確認

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## IntelliJ IDEA のセットアップ

### コードフォーマット（Google Java Style）

BootHarnessはSpotlessで[google-java-format](https://github.com/google/google-java-format)を強制しています。

**ステップ1：プラグインをインストール**

`Settings → Plugins → Marketplace` → **google-java-format** を検索 → Install → 再起動

**ステップ2：有効化**

`Settings → google-java-format Settings` → **Enable google-java-format** にチェック

**ステップ3：保存時に自動フォーマット**

`Settings → Tools → Actions on Save` → **Reformat code** にチェック

設定後、Javaファイルは保存のたびにGoogle Java Style（2スペースインデント、100文字制限）で自動整形されます。

> **補足:** プラグインがうまく動かない場合は `./gradlew spotlessApply` で全ファイルを一括修正できます。`./gradlew check` はフォーマットが崩れていると失敗します。

### EditorConfig

IntelliJは `.editorconfig` を自動で読み込みます。追加設定不要です。

### プロジェクトのインポート

プロジェクトルートをIntelliJで **Gradleプロジェクト** として開くと、`build.gradle` が自動検出され依存関係がインポートされます。

---

## フォーマット関連コマンド

| コマンド | 説明 |
|---------|------|
| `./gradlew spotlessApply` | フォーマット違反を自動修正 |
| `./gradlew spotlessCheck` | フォーマットチェック（違反があれば失敗） |
| `./gradlew check` | テスト + フォーマットチェックを実行 |
