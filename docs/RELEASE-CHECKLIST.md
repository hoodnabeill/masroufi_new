# Masroufi 2.0.0: release checklist

Everything here is a step or a decision only the owner can take, because it needs the Play Console, a real phone, a
price, a legal choice or a taste call. Each line says where it lives, so nobody has to search. Tick them in order:
section 1 blocks everything after it. Built by Task 65 from the carry-forward
(`docs/superpowers/plans/2026-11-13-v2-foundation-carry-forward.md`) and the Phase 13 plan.

## 1. Accounts, signing and the build that gets uploaded

- [ ] **Google Play Console account** (one-time fee, identity check). Create the app "Masroufi" (default language
      Arabic) with package `com.masroufi.app`. Nothing below can be filled in without it.
- [ ] **Signing key backup.** CI signs releases with the keystore decoded from the `MASROUFI_KEYSTORE_BASE64` secret
      (`.github/workflows/android.yml`, `app/build.gradle.kts` `signingConfigs.release`; secrets `MASROUFI_KEYSTORE_*`,
      `MASROUFI_KEY_ALIAS`, `MASROUFI_KEY_PASSWORD`). Keep the `.jks` file and its three passwords in two places you
      control (password manager plus an offline copy). Lose it and a signed app can never be updated under that key.
      Decide whether to enrol in **Play App Signing** (recommended: then the file CI uses is only the upload key and a
      lost one can be reset through Play).
- [ ] **Release must be signed with the real key.** With no secrets the release build falls back to the public debug
      key (`app/build.gradle.kts`, `signingConfig = ... "debug"`). Check the uploaded file is not signed with it:
      `apksigner verify --print-certs` on the APK, or look at the certificate in Play's App integrity page.
- [ ] **An `.aab`, not an APK.** Play takes an Android App Bundle for new apps and CI only runs `assembleRelease`.
      Add `bundleRelease` (and upload the bundle as an artifact) before the first upload.
- [ ] **Version code.** `versionCode` is the CI run number (`VERSION_CODE`); `versionName` is `2.0.0`
      (`app/build.gradle.kts`). Upload from one CI run and keep the code rising.
- [ ] **Target API.** `targetSdk = 35`. Check Play's current requirement for new apps and raise it if needed before
      the first upload.
- [ ] **Room schemas.** `app/schemas/.../*.json` (database version 8) were never committed; they come from the
      `room-schemas` CI artifact (downloading it needs a GitHub login). Commit them once so the next migration is
      checked against them (`TazkaraDatabase.kt` comment).
- [ ] **v1 users.** v2 uses its own files (`tazkara.db`, `tazkara_settings`) and does not read v1's data; "Delete all
      my data" removes v1's old files. If v1 was ever published, decide what the update does to its users (nothing
      migrates today).

## 2. Plus: products, Billing, prices

- [ ] **Create the two subscription products** in Play Console → Monetize → Subscriptions. Proposed IDs (nothing in
      code uses an ID yet): `masroufi_plus_yearly` and `masroufi_plus_monthly`, each with one auto-renewing base plan
      (yearly / monthly), grace period on, "resubscribe" on.
- [ ] **Write the real Google Play Billing implementation.** Today `StubBilling` (`entitlements/Billing.kt`) is bound
      in `di/AppModule.kt` (`fun billing`): in release it has no products, so the paywall says "prices not available
      yet" and a purchase always fails. Replace it with an implementation of the `Billing` interface using the Play
      Billing library: `products` from the product details (`BillingProduct.priceText` = the store's own formatting,
      never hard-coded), `purchase`, automatic restore on start, `restore()`, `subscription` (plan + renewal date),
      `manageSubscriptionUrl()`. The paywall (`feature/paywall`), the trial and the entitlement rules
      (`entitlements/PlusRepository.kt`) are written against the interface and need no change. It is not testable
      without the Console products and a licence-tester account.
- [ ] **Billing side effects.** The library adds `com.android.vending.BILLING` and may add `INTERNET`. Then fix, in the
      same change: the "no INTERNET" lines in `docs/legal/privacy-policy.md` and `terms.md`, the in-app legal text
      (`feature/settings/LegalScreens.kt` and its strings), `docs/legal/PLAY_CONSOLE.md` (Permissions, Data safety),
      and both listing files. Re-read the merged permission list in the CI notice "Merged permissions".
- [ ] **Prices.** Proposal in 09-PREMIUM: yearly 399 EGP (shown "يعني 33 جنيه في الشهر", "وفّر 32%"), monthly 49 EGP.
      Decide, set them per country in Play Console, and check the "33" and "32%" figures are computed from the real
      prices (the paywall does it from `priceMicros`) and still true.
- [ ] **Trial length.** 14 days of Plus, offered from day 7 of use, no card (`TRIAL_DAYS = 14`,
      `TRIAL_OFFER_AFTER_DAYS = 6` in `entitlements/PlusRepository.kt`). The listings say "14-day trial": change them
      if you change the number.
- [ ] **Listing's Plus paragraph is true only after Billing ships.** Do not publish the listing with Plus described
      while the release build cannot buy it (or word it as "coming soon").
- [ ] **Subscription wording.** Check once more that listing, paywall and terms say: price and period from Google Play,
      renews automatically, cancel in Google Play → Subscriptions, the free version stays usable (09-PREMIUM).

## 3. Play Console forms

Answers are written in `docs/legal/PLAY_CONSOLE.md`; paste them in.

- [ ] **Privacy policy URL.** Turn on GitHub Pages for the repository's `docs/` folder (Settings → Pages) and use the
      URL of `docs/legal/privacy-policy.md`. The repository must stay public for this. Check it opens on a phone.
- [ ] **Support email.** `SUPPORT_EMAIL` is an empty string in `feature/settings/SettingsScreen.kt` (line 80), so the
      feedback row is hidden and the legal text points to the Play page. Choose an address, set the constant, and
      enter the same one in Play Console's store settings. Read `LegalScreens.kt` after, to see the contact line.
- [ ] **Data safety, content rating, target audience, ads, financial features.** Fill them as written. Decide the
      audience: the text says 18 and over; 01-PRODUCT also names students, so decide whether to include 16-17.
- [ ] **Store listing.** Paste `docs/store/listing-ar.md` (default) and `listing-en.md`. Upload from the CI artifact
      `store-assets`: `icon-512.png`, `feature-graphic-1024x500.png`, the phone screenshots in `screenshots/ar/` and
      `screenshots/en/`. Look at every picture first (they were rendered by tests nobody has seen on a device; the
      tag "بيانات تجريبية · sample data" is part of each screenshot) and drop any that look wrong. Play wants 2 to 8
      phone screenshots per language.
- [ ] **SMS permission question.** Today the app never asks for SMS (the user pastes a bank message). Auto-reading
      incoming messages is shown as "قريب" in Settings and is a Plus line in 02-FEATURES, but it is not built and not
      in the listing. Decide: never (remove the Settings row and the Plus table line), or later (needs Play's
      Permissions Declaration Form and an approval that banks-SMS apps often do not get).

## 4. Decisions only the owner can take

- [ ] **The last 10 proverbs.** The spec says 60 cards; the catalog has 50 (`domain/ProverbCatalog.kt`,
      `PROVERB_SET_SIZE` reads the real size; the album says "N of 50"). Write ten more or keep 50 and fix the
      spec/listing wording (the listing says no number).
- [ ] **Gold price.** Is it ever fetched? Today the user types the price per gram (`goldPricePerGram`, "عدّل السعر")
      and the app has no internet permission. Fetching it needs `INTERNET`, a data source, and updates to the privacy
      policy, Data safety and the listing. Keeping it typed needs nothing.
- [ ] **Kids' allowance limits.** Free = one child (`FREE_CHILDREN` in `data/repo/KidsRepository.kt`); an advance may
      not exceed half the weekly allowance less what was already approved (`KidsRepository.kt`, line near 352);
      Plus = unlimited. Confirm or change. Other free limits: 4 envelopes (`FREE_ENVELOPES`), 1 gameya
      (`FREE_GAMEYAS`), 4 league weeks (`FREE_LEAGUE_WEEKS`).
- [ ] **PIN length is stored unencrypted.** The lock keypad submits itself at the PIN's length as drawn, so the
      length (4 to 6) sits in the settings DataStore unencrypted (`core/PinStore.kt`, `pinLength`); the PIN itself is
      PBKDF2-hashed and sealed with a Keystore key. Keep it, or switch to an OK key that hides the length.
- [ ] **Close-from-notification actions** (02-FEATURES "اقطعها للحصّالة" / "بعد ساعة") are not built; the listing does
      not mention them. Decide whether they ship in 2.0.0.
- [ ] **Restore from onboarding** is not built (restore is in Settings → backup). Decide if a new phone needs it.
- [ ] **Not-built Plus line:** auto-reading of incoming SMS (see section 3).

## 5. Copy review: every "guessed copy" line

The builder wrote these words; they are not in 07-COPY.md. Read each on a screen and fix or approve. All English
strings in `values-en/` are also a first draft and need a native read (Phase 11's own note).

- **Phase 3 (daily loop):** the streak's TalkBack label; plurals such as "في 1 مرات"; the transfer wording (حوّل / إلى /
  في / "من {from} لـ {to}"); leftover destinations لبكره / للطوارئ / نادر; the share-failure toast; every string in
  `values-en/`.
- **Phase 7 (people):** the whole duty ledger copy (07-COPY has none); the gameya statement text and the
  shared-subscription person name are Arabic literals in data code, so English users see Arabic there.
- **Phase 8 (match, buying, album, profile):** "رخصت عليك", "ثابتة", the basket suggestion body; the 0.5 ticket "نص".
- **Phase 9:** end-of-month: the paused-habit line, "وقّف الوضع", the offer text, the shortfall hero.
- **Phase 9b (story, kids):** the story's opening line, the money and jar kickers, the four suggestion lines, the
  neutral line, the empty list text; kids: empty title, forms, PIN lines, child notices.
- **Phase 10 (Plus):** headline variants, tags, row titles, subs and pitches, trial card and line, "prices
  unavailable", the restore lines.
- **Phase 11 (trust):** all legal text (`docs/legal/*` and the in-app copy); the lock strings.
- **Phase 11 (notifications):** the evening line without an amount, the Plus and subscription trial lines, low
  balance, occasion without a gift, channel names, the settings lines.
- **Widgets:** "قفلت النهارده", "عدّيت التذكرة", the widget names and descriptions.
- **Plan:** the 07-COPY-less payday "where it went" labels follow the mock colours, not the text; category icons for
  فواتير and صحة are borrowed (bell, grid): decide if two new icons are worth drawing.
- **Other drawn-vs-built differences to look at:** the suggested ticket follows the rule (240 x 1.09 = 261), not the
  mock's 252; freelance daily ticket 225 where the mock shows 233; the birthday plan 8.20/day where the mock shows 8;
  safe-until shows a date, not "آخر يناير"; no sparkline in the basket; the delivery-habit fact is not built.

## 6. Device test pass (nothing below has ever run on a real phone)

Everything was built and tested on CI only (Robolectric). One pass on a real phone, ideally two (a current Android
and an older one near minSdk 26), RTL Arabic and LTR English.

- [ ] **Install and first run.** Fresh install of the release (signed, R8-minified) build, not debug. Onboarding to the
      first ticket. Then an update install over an older build of 2.0.0 (the CI debug key is stable, the release key
      only with secrets).
- [ ] **R8 release build behaviour.** The keep rules (Task 64) are reasoned, not run: widgets and the widget chip
      callback (Glance `ActionCallback` by name), WorkManager workers (names stored in its DB), Hilt entry points,
      the story JSON and day summary (kotlinx.serialization), Room, biometric. Walk every screen in the release APK
      and read `adb logcat` for `ClassNotFoundException`, `NoSuchMethodException`, serialization errors. Keep
      `release-mapping` (CI artifact) with each upload to read stack traces.
- [ ] **Icon and splash.** The new icon was never rendered on a device: check the launcher icon in a circle, a squircle
      and a square mask, the round icon, the themed (monochrome) icon on Android 13+, and small sizes (no dashed
      perforation). Splash look (night, ticket at 96 dp), its exit (slides 12 dp and fades; Home's ticket does not
      start from it, so judge whether the two line up). Also the 512 PNG in the CI artifact.
- [ ] **Lock and biometric.** PIN set/change/off; cold start locked; relock after 60 seconds away; five wrong tries
      and the waiting time; fingerprint prompt (`BiometricPrompt` is compile-checked only); a forgotten PIN path;
      the Keystore key lost (system prompt clears the PIN and turns the lock off); PIN on a restored backup reads as
      unreadable on a new phone.
- [ ] **FLAG_SECURE.** With the lock on, the app switcher shows a blank card and screenshots are blocked; with the lock
      off they are not. Hide amounts covers every screen listed in the Phase 11/12 notes.
- [ ] **Backup and restore.** The SAF pickers (create file, open file, folder), the folder prune of old weekly files,
      the Keystore-sealed backup password, wrong password vs damaged file messages, restore then the app relaunch,
      the backup password must be set again after a restore, the CSV export opens in a spreadsheet app. Run on a phone
      with a cloud-backed folder too.
- [ ] **WorkManager timing.** The midnight print (00:05 daily), the evening notification hours, the weekly backup
      (7 days, charging + idle, first run Friday 02:00), re-arming after a reboot and after force-stop, battery
      saver and doze. Never run on a device so far.
- [ ] **Notifications and permission prompts.** POST_NOTIFICATIONS asked on Android 13+ when a reminder is turned on
      (and the denied path), each channel, taps through `NotificationRouter` to the right screen after the lock,
      dots on the lock-screen text when lock or hide amounts is on, two a day at most, quiet windows.
- [ ] **Widgets.** Place all three (small ticket, small match, wide), previews in the picker, dark and light wallpaper,
      the chip callback logging an entry (lock on, child mode, a chip the summary no longer shows: all do nothing),
      the match widget opening Match, dots with the lock on, refresh after midnight and after a punch.
- [ ] **Child screen and kids.** Child mode beats onboarding at start-up, only the child screen shows and back is
      swallowed, leaving needs the PIN or fingerprint, handover, advance, chores, the child's jar match, the weekly
      week key, "delete all my data" while in child mode.
- [ ] **Delete all my data.** It relaunches `MainActivity` in a cleared task and kills the process: verify it really
      lands on onboarding with nothing left (and v1's old files gone).
- [ ] **Voice and typing.** The Home mic button (system recognizer, only shown if one exists), the quick log text
      ("20 فول وطعمية"), the system keyboard on the unit price, the amount keypad.
- [ ] **Touch feel and haptics.** The touch-down press delay inside scrolling lists, every haptic level, ticket sounds
      (off by default), "remove animations" on, font scale 200% on real screens, TalkBack on Home, the add sheet and
      the roll.
- [ ] **Screens never seen on a device:** the sheet that rises from the bottom (not out of the + button), the story's
      tap halves and share image (1080 x 2337 PNG through the FileProvider), the status-bar icon colour on the story's
      yellow, the birthday picker, the month and income charts at large fonts, the Plan "Gallery" link must not appear
      in release (it is debug only).
- [ ] **Play pre-launch report.** After the first upload to an internal testing track, read Google's pre-launch report
      (crashes, accessibility) and fix what it finds.
