# Masroufi (مصروفي)

Masroufi v2 ("Tazkara") is a ground-up rebuild of the offline-first personal finance app for Android: Arabic-first, English supported, no account, no analytics, no `INTERNET` permission.

This branch (`v2`) starts from an empty app. The product and visual spec lives in [`masroufi-tazkara-handoff/`](masroufi-tazkara-handoff/); the design doc is [`docs/superpowers/specs/2026-11-13-masroufi-v2-tazkara-design.md`](docs/superpowers/specs/2026-11-13-masroufi-v2-tazkara-design.md) and the plan is in `docs/superpowers/plans/`.

Stack: Kotlin 2.2, Jetpack Compose (Material 3), Room, Hilt, DataStore, WorkManager.

## Building

CI (`.github/workflows/android.yml`) runs the unit tests and builds the debug and release APKs on every push. The APKs are uploaded as the `masroufi-apks` artifact.

The repository has no Gradle wrapper JAR, so CI provisions Gradle 8.11.1 directly. To build locally with JDK 17 and the Android SDK, run:

```
gradle wrapper --gradle-version 8.11.1
./gradlew assembleDebug testDebugUnitTest
```

**Release signing.** Add the `MASROUFI_KEYSTORE_BASE64`, `MASROUFI_KEYSTORE_PASSWORD`, `MASROUFI_KEY_ALIAS` and `MASROUFI_KEY_PASSWORD` repository secrets. Without them, the release APK is signed with the debug key so it can still be installed for testing. Don't publish that build.
