# Masroufi (مصروفي)

Masroufi is an offline-first personal finance app for Android, built mainly for Egyptian users. It is Arabic-first, with full English support. You don't need an account, and there is no analytics and no cloud dependency.

## Features

- **Wallets**: cash, bank, digital and custom wallets, each with its own currency. Balances are calculated from transactions. Totals are never added across currencies.
- **Transactions**: income, expenses and transfers (a single atomic row), including transfers between currencies with a received amount. Each transaction can have categories and subcategories, notes, tags, a date and a private receipt photo. Search, filters and sorting are supported.
- **Home dashboard**: balance, this month's income and expenses, a spending donut, budgets, goals, upcoming recurring items, debts and recent transactions. You can choose which sections appear.
- **Budgets**: monthly or custom-period budgets for a category or for all spending. They show warnings at 80% and when over budget, offer optional rollover, and compare with last month.
- **Savings goals**: contributions and withdrawals recorded as earmarks, separate from wallet balances so nothing is counted twice. Goals have target dates, and an estimated completion date appears only after 30 days of history.
- **Recurring transactions**: daily, weekly, monthly or yearly, every N periods, with an end date, pause/resume and reminders. Generation is idempotent: occurrence ids are deterministic and protected by a unique index.
- **Gameya (جمعية)**: members in payout order, per-round payment tracking for every member, payout received, and your own obligation, paid amount, remaining amount and turn. Rounds can be monthly or every N days.
- **Debts and lending**: partial repayments are validated inside a database transaction so a debt can never be over-repaid.
- **Auto-split**: fixed amounts are taken first, then percentages of what remains. Shares round down and the remainder is shown as unallocated. You preview the split before confirming. Lines linked to a goal become goal contributions, and a split can never be applied twice.
- **Analytics and insights**: income vs expenses by month, category breakdown, net cash flow, budget adherence and obligations. Facts and estimates are labelled separately.
- **Backup**: encrypted export and restore (AES-256-GCM, PBKDF2 key). Restore lets you merge or replace, and it checks the password before changing anything.
- **WebDAV sync**: data is encrypted on the device before upload. Uploads are guarded by If-Match / ETag. Records are merged by id with newest-edit-wins, and conflicts are counted and shown.
- **Security**: PIN lock (salted PBKDF2, with exponential back-off after failed attempts), BiometricPrompt, a lock timeout, and FLAG_SECURE so balances don't show in recents. Notifications hide amounts on the lock screen.
- **Entitlements**: a single `Entitlements` class decides feature access. Billing doesn't exist yet, so every feature is unlocked during early access and the app says so.

## Architecture

The app is a single `:app` module with feature packages:

| Package | Contents |
|---|---|
| `core` | Money (Long minor units, BigDecimal only at text boundaries) and dates |
| `domain` | Pure, unit-tested logic: recurrence, budgets, split, gameya, debts, goals, insights |
| `data.model` / `data.db` | Room entities and DAOs. Every row has `id`, `updatedAt` and a `deleted` tombstone for sync |
| `data.repo` | Validation and atomic writes. Errors are surfaced as `FinanceException(FinanceError)` |
| `backup` | Snapshot, crypto, WebDAV client, merge |
| `security` | Keystore cipher, PIN hashing, app lock |
| `work` | Daily worker (recurring items and reminders) and sync worker |
| `ui` / `feature.*` | Compose UI, one ViewModel per screen, `StateFlow` state plus a `Channel` for effects |

Stack: Kotlin 2.1, Compose (BOM 2024.12.01, Material 3), Room 2.6, Hilt 2.54, Navigation Compose 2.8 (type-safe routes), DataStore, WorkManager, BiometricPrompt, OkHttp, Coil and Haze 1.5 (real-time glass blur for the floating dock).

**Design system.** Ambient aurora light behind every screen, glass cards with light-catching edges, a floating liquid-glass dock, odometer amounts and Apple-style critically damped springs (stiffness = (2π/response)²). Fonts: Inter (Latin) and IBM Plex Sans Arabic (Arabic), both under the SIL Open Font License (see `licenses/`). All motion respects the system "remove animations" setting.

## Building

CI (`.github/workflows/android.yml`) runs the unit tests and builds the debug and release APKs on every push. The APKs are uploaded as the `masroufi-apks` artifact.

The repository has no Gradle wrapper JAR, so CI provisions Gradle 8.11.1 directly. To build locally with JDK 17 and the Android SDK, run:

```
gradle wrapper --gradle-version 8.11.1
./gradlew assembleDebug testDebugUnitTest
```

**Release signing.** Add the `MASROUFI_KEYSTORE_BASE64`, `MASROUFI_KEYSTORE_PASSWORD`, `MASROUFI_KEY_ALIAS` and `MASROUFI_KEY_PASSWORD` repository secrets. Without them, the release APK is signed with the debug key so it can still be installed for testing. Don't publish that build.

## Security model (honest version)

- **Database**: the Room database is **not** encrypted by the app. It lives in app-private storage, which Android isolates per app, and Android file-based encryption protects it while the device is locked. A rooted or compromised device can read it. SQLCipher is the upgrade path if at-rest database encryption becomes a requirement.
- **Backups and sync payloads**: encrypted with AES-256-GCM, using a key derived from the user's password with PBKDF2-HMAC-SHA256 (210k iterations). The header is authenticated. Receipt images are excluded.
- **WebDAV password and sync passphrase**: encrypted with a non-exportable AES-GCM key in Android Keystore.
- **PIN**: only a salted PBKDF2 hash is stored. After 5 failures, attempts are throttled with exponential back-off.
- **Network**: only HTTPS WebDAV URLs are accepted (loopback is allowed for testing). The server only ever sees ciphertext.
- **Android backup**: `allowBackup` is off and data-extraction rules exclude everything, so financial data never goes to Google Drive automatically.

## Known limits

- Room schema is version 1. Future versions need explicit migrations, and exported schemas are uploaded from CI as `room-schemas`.
- The sync merge is row-level last-writer-wins. It reports conflicts but doesn't offer field-level resolution.
- There is no exchange-rate service. Cross-currency transfers record the actual amount received.
