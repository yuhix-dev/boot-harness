# Getting Started

## Prerequisites

- Java 21+
- Docker & Docker Compose
- IntelliJ IDEA (recommended)

## 1. Clone and configure

```bash
git clone <your-repo-url>
cd your-project
cp .env.example .env   # then fill in values
```

At minimum, set these in `.env` before starting:

```bash
JWT_SECRET=<run: openssl rand -base64 64>
DB_PASSWORD=your-local-password
```

## 2. Start the database

```bash
docker-compose up db -d
```

## 3. Run the app

```bash
./gradlew bootRun
```

Flyway migrations run automatically on startup. The API is available at `http://localhost:8080`.

## 4. Verify

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

---

## IntelliJ IDEA Setup

### Code formatting (Google Java Style)

BootHarness uses [google-java-format](https://github.com/google/google-java-format) enforced by Spotless.

**Step 1: Install the plugin**

`Settings → Plugins → Marketplace` → search **google-java-format** → Install → Restart

**Step 2: Enable it**

`Settings → google-java-format Settings` → check **Enable google-java-format**

**Step 3: Format on save**

`Settings → Tools → Actions on Save` → check **Reformat code**

After this, IntelliJ will auto-format Java files on every save using Google Java Style (2-space indent, 100-char line limit).

> **Note:** If the plugin isn't formatting correctly, you can always run `./gradlew spotlessApply` to fix all files at once. `./gradlew check` will fail if formatting is off.

### EditorConfig

IntelliJ reads `.editorconfig` automatically. No additional setup needed — indent sizes, line endings, and charset are applied per file type.

### Import the application

Open the project root in IntelliJ as a **Gradle project**. IntelliJ will auto-detect the `build.gradle` and import dependencies.

---

## Code formatting commands

| Command | Description |
|---------|-------------|
| `./gradlew spotlessApply` | Auto-fix all formatting issues |
| `./gradlew spotlessCheck` | Check formatting (fails if violations found) |
| `./gradlew check` | Run tests + formatting check |
