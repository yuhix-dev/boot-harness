# メール

BootHarnessは[Resend](https://resend.com)経由でトランザクションメールを送信します。実装はプロバイダー非依存で、`EmailRepository` の実装を差し替えることでプロバイダーを変更できます。

## 仕組み

メール送信はイベント駆動です。各サービスがSpring Application Eventをパブリッシュし、`EmailService` がリッスンして適切なメールを送信します。

| イベント | 送信されるメール |
|---|---|
| `UserRegisteredEvent` | ウェルカムメール |
| `PasswordResetRequestedEvent` | パスワードリセットリンク |

## セットアップ

1. [Resend](https://resend.com) アカウントを作成してAPIキーを取得
2. Resendダッシュボードで送信ドメインを認証
3. 環境変数を設定:

```bash
RESEND_API_KEY=re_...
EMAIL_FROM=noreply@yourdomain.com
```

## テンプレートのカスタマイズ

メールテンプレートは `EmailService.java` のプライベートメソッドにHTMLとして定義されています。`welcomeHtml()` と `passwordResetHtml()` を直接編集してください。

## メールプロバイダーの差し替え

`EmailRepository` を実装して `@Primary` を付けるだけです:

```java
@Repository
@Primary
public class SendGridEmailRepository implements EmailRepository {
    @Override
    public void send(String to, String subject, String htmlBody) {
        // SendGrid実装
    }
}
```

## カスタムメールの送信

`EmailRepository` を直接インジェクトして `send()` を呼び出します:

```java
@RequiredArgsConstructor
public class MyService {
    private final EmailRepository emailRepository;

    public void sendInvoice(String email, String html) {
        emailRepository.send(email, "請求書", html);
    }
}
```

## 環境変数

| 変数 | 説明 |
|---|---|
| `RESEND_API_KEY` | Resend APIキー |
| `EMAIL_FROM` | 認証済み送信者アドレス（例: `noreply@yourdomain.com`）|
