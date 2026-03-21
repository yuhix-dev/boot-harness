# Email

BootHarness sends transactional emails via [Resend](https://resend.com). The implementation is provider-agnostic — swap the `EmailRepository` implementation to switch providers.

## How It Works

Email sending is event-driven. Services publish Spring Application Events; `EmailService` listens and sends the appropriate email.

| Event | Email sent |
|---|---|
| `UserRegisteredEvent` | Welcome email |
| `PasswordResetRequestedEvent` | Password reset link |

## Setup

1. Create a [Resend](https://resend.com) account and get an API key
2. Verify your sender domain in the Resend dashboard
3. Set environment variables:

```bash
RESEND_API_KEY=re_...
EMAIL_FROM=noreply@yourdomain.com
```

## Customizing Templates

Email templates are in `EmailService.java` as private methods returning HTML strings. Edit `welcomeHtml()` and `passwordResetHtml()` directly.

## Swapping Email Providers

Implement `EmailRepository` and annotate it with `@Primary`:

```java
@Repository
@Primary
public class SendGridEmailRepository implements EmailRepository {
    @Override
    public void send(String to, String subject, String htmlBody) {
        // SendGrid implementation
    }
}
```

## Sending Custom Emails

Inject `EmailRepository` directly and call `send()`:

```java
@RequiredArgsConstructor
public class MyService {
    private final EmailRepository emailRepository;

    public void sendInvoice(String email, String html) {
        emailRepository.send(email, "Your invoice", html);
    }
}
```

## Environment Variables

| Variable | Description |
|---|---|
| `RESEND_API_KEY` | Resend API key |
| `EMAIL_FROM` | Verified sender address (e.g. `noreply@yourdomain.com`) |
