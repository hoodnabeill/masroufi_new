# Masroufi v2 Phase 13 (store readiness) Implementation Plan

> **For agentic workers:** one implementer subagent per task, one at a time (owner instruction); done when CI is green on its head commit; no reviewer loop unless asked.

**Goal:** A release build ready to upload: the icon in every required form, the splash, version 2.0.0, signing, a safe R8 build, store assets and listing text in Arabic and English, screenshots from real screens with sample data marked as sample, and the Play Console answers — plus one release checklist naming every decision only the owner can make.

**Spec:** `12-BUILD-PROMPTS.md` Prompt 13; `13-ICON.md` (icon, round, themed, small sizes, store 512, splash); `09-PREMIUM.md` ("Wording for stores and legal"); `01-PRODUCT.md` (what to say about the app); `docs/legal/PLAY_CONSOLE.md`; the carry-forward doc (open owner decisions).

## Global constraints

As Phases 3–12. No new features. CI only (no local JDK). Never commit secrets; signing stays through the existing CI secrets (`MASROUFI_KEYSTORE_*`).

## Decisions taken in this plan (owner may override)

1. **Billing stays the stand-in** (owner decision: no Play Console yet). The release build shows "prices not available yet" on the paywall. The real Google Play Billing implementation is listed in the release checklist as the first step once the Play Console account and the two products exist.
2. **Store assets are rendered by CI**, not hand-drawn: a Robolectric test renders the icon at 512 px and a 1024 × 500 feature graphic from the app's own drawables/composables, and the screenshot tests render key screens at phone size with sample data and a visible "بيانات تجريبية · sample data" tag; CI uploads them as an artifact (`store-assets`).
3. **Privacy policy URL:** GitHub Pages on `docs/` (already suggested in `PLAY_CONSOLE.md`); turning Pages on is the owner's step and is in the checklist.

---

## Task 64: Release build — icon, splash, version, R8

- Icon per `13-ICON.md`: check the existing adaptive foreground/background/monochrome against the spec (ticket 68% wide tilted 10° anticlockwise, notches two-thirds along, the punched hole, the turquoise fin, lapis night background, artwork inside the middle 66%, guilloche dropped), fix what differs; round icon (85%); themed (monochrome single shape); small-size variant (no perforation ≤ 48 px) where Android allows; legacy `mipmap` PNGs are not needed (minSdk 26) — say so.
- Splash per `13-ICON.md` with `androidx.core:core-splashscreen` (add it) — night background, the ticket alone at 96 dp; the hand-off to Home's ticket is a simple fade if the "prints down 12 dp" hand-off is not feasible (say which).
- `versionName = "2.0.0"`; `versionCode` stays the CI run number.
- R8: review keep rules for everything reflective (kotlinx.serialization payloads such as the story JSON and the day summary, Hilt workers, Glance receivers and action callbacks, Room, DataStore, biometric, navigation routes); add rules where needed; upload `mapping.txt` as a CI artifact; make sure `assembleRelease` with minify stays green.
- Tests: a resource test that the launcher, round and monochrome icons resolve; the splash theme is applied to the launcher activity.

## Task 65: Store assets, listing text, Play answers, release checklist

- Store assets (decision 2): the 512 icon, the 1024 × 500 feature graphic, and 6–8 phone screenshots in Arabic (Home with a ticket, the add sheet, close day + proverb, the roll, the Plan tab envelopes, People, the match or album, the jar) plus the same set in English, from real screens with sample data and the sample tag; CI artifact `store-assets`.
- Listing text in `docs/store/` (Arabic and English): app name, short description (≤ 80 characters), full description (≤ 4000 characters) built from `01-PRODUCT.md` and `02-FEATURES.md`, the Plus wording required by `09-PREMIUM.md` (price and period from the store, auto-renewal, how to cancel, the free version stays usable), and nothing that is not true today (no SMS auto-reading, no cloud sync).
- `docs/legal/PLAY_CONSOLE.md`: update to the shipped facts (permissions actually in the merged manifest incl. any added by libraries, data safety, content rating answers, target audience, the SMS line).
- `docs/RELEASE-CHECKLIST.md`: every step and decision only the owner can take — Play Console account, the two subscription products and the real Billing implementation, prices and trial length, GitHub Pages for the privacy policy URL, the support email (`SUPPORT_EMAIL`), the last 10 proverbs (catalog is 50), whether the gold price is ever fetched, kids' allowance limits, the SMS permission question, reviewing every guessed-copy line in the carry-forward, signing key backup, and a device test pass (the list of things never run on a device, from the carry-forward).
