# Google Play Console notes

Answers for the Play Console forms, matching what Masroufi 2.0.0 (version code = the CI run number) does as of
Phase 13. Re-check them whenever a feature that touches data, permissions or the network changes, and again when real
Google Play Billing replaces `StubBilling` (it adds the network and a billing permission, see "Permissions"). This is
not legal advice. The store texts are in `docs/store/listing-ar.md` and `docs/store/listing-en.md`; every open decision
is in `docs/RELEASE-CHECKLIST.md`.

## Privacy policy URL

Play requires a public URL. The repository is public, so the simplest option is GitHub Pages on `docs/`:
publish `docs/legal/privacy-policy.md` and paste its URL into **App content → Privacy policy**. The same text ships
inside the app (Settings → Privacy and terms), in Arabic and English, and `docs/legal/*` is written from it. Turning
Pages on is the owner's step (checklist).

## App access, ads, and the short declarations

- **App access:** no sign-in exists, so everything works without special access. Answer "All functionality is available
  without special access".
- **Ads:** none. The app has no ad SDK. Answer "No, my app does not contain ads".
- **Advertising ID:** not used (no ad or analytics library). Answer "No".
- **Account deletion:** there is no account. The in-app way to erase everything is Profile → Delete all my data.
- **Government app, news app, health app, COVID-19 app:** no to all.

## Financial features declaration

Masroufi is a personal-money notebook. Declare **"My app does not provide any of these financial features"**, or, if
the form lists a "personal finance management / budgeting" option, tick only that. It does not lend money, hold money,
move money, take payments for third parties, trade, or give advice. The "loans" screens (سلف / النوتة) are records of
what friends and family owe each other, typed by the user: there is no lending, no interest and no collection, so the
personal-loan policy does not apply. Bank and wallet names are plain text labels.

## Data safety form

- **Does the app collect or share any of the required user data types?** No. Everything is stored only on the
  device. There is no account, no server, no analytics, no ads and no crash-reporting SDK.
- **Is data encrypted in transit?** Not applicable: the app itself never uses the network and does not declare the
  `INTERNET` permission. Backups are files the user saves, encrypted with the user's password (AES-256-GCM, key from
  the password with PBKDF2).
- **Can users request deletion?** Yes: Profile → Delete all my data, Android's clear-storage, or uninstall.
- **Voice input:** the microphone button on Home asks Android's own speech recognizer (an intent). The app has no
  `RECORD_AUDIO` permission and receives only the text. Any audio handling belongs to the system recognizer, not to
  Masroufi, so it is not declared as data the app collects.
- **Sharing:** images and text the user chooses to share (the month's story, a proverb card, a gentle reminder) go to
  the app the user picks, through Android's share sheet. That is a user-initiated action, not data sharing by the app.
- Google Play Billing (checklist item) is handled by Google Play, not by the app's own network code. When Billing is
  added, purchase data is processed by Google; the Data safety form still answers "No data collected" for the app
  itself unless the library or a server is added. If you ever add analytics, crash reporting, accounts, ads or a
  server, this form must be updated first.

## Permissions

Declared by the app (`app/src/main/AndroidManifest.xml`):

| Permission | Why |
|---|---|
| `POST_NOTIFICATIONS` | reminders the user turns on (asked on Android 13+ when a reminder is enabled) |
| `USE_BIOMETRIC` | the app lock's fingerprint |
| `VIBRATE` | the feel of a tap (can be turned off) |

Added by libraries through manifest merging (expected from the dependency list; the CI step "Merged permissions"
prints the real list of the release APK as a notice, and this table must be fixed to match it):

| Permission | From | Note |
|---|---|---|
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `ACCESS_NETWORK_STATE` | WorkManager (`work-runtime`) | the midnight ticket print, the notification run and the weekly backup are WorkManager jobs; the app starts no foreground service of its own and does not use the network |
| `USE_FINGERPRINT` | `androidx.biometric` | legacy fingerprint, up to Android 8 |
| `<package>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | a signature permission private to the app |

Glance (widgets), core-splashscreen, Room, DataStore, Hilt and Compose add none.

Not declared: `INTERNET`, `RECEIVE_SMS`, `READ_SMS`, `RECORD_AUDIO`, contacts, storage, location, exact alarms. If the
merged list printed by CI shows `INTERNET`, stop: the privacy policy, the terms, the in-app legal text and the listing
all say the app has none.

When real Google Play Billing is added it brings `com.android.vending.BILLING` (and the library may bring
`INTERNET`): update this table, the privacy policy ("does not ask for the internet permission"), the terms, the
in-app legal text and the two listing files in the same change.

## SMS permission declaration

Not needed. v2 never requests an SMS permission: the user pastes a bank or wallet message and the app reads it on
the device, never storing or sending the text. Auto-reading of incoming messages ("read bank messages", shown as
"قريب" in Settings, a Plus feature in the plan) is not built and is not in the listing. If a later version asks for
incoming-SMS access, the Permissions Declaration Form (an approved core-use exception) must be filled in first. This
is an owner decision in the checklist.

## Subscriptions (Plus)

State in the listing and in the app: the price and period (from Google Play, never typed in the app), that it renews
automatically, that it is cancelled in Google Play → Subscriptions, and that the free version stays usable without
paying (09-PREMIUM, "Wording for stores and legal"). The in-app terms and both listing files already say this. The
release build today shows "prices not available yet" because no store is connected; the Plus paragraph of the listing
goes live only with the real Billing implementation.

## Content rating and audience

- **Content rating (IARC questionnaire):** category "Utility, productivity, communication or other" (a finance
  utility). Violence: no. Sexual content: no. Profanity: no. Controlled substances: no. Gambling or simulated
  gambling: no (the proverb cards and the weekly match are not bets and cost nothing). User-generated content shared
  with other users: no. Users can communicate with each other in the app: no. Shares the user's location: no. Digital
  purchases: yes, once Plus can be bought. Expected result: Everyone / PEGI 3 / IARC 3+.
- **Target audience:** 18 and over. The app is not designed for children under 13 and does not appeal to them as a
  primary audience; the kids' allowance screens are a parent's tool on the parent's phone (no child account, no child
  data leaves it), so the Families policy does not apply. 01-PRODUCT also names students and young adults: whether to
  tick 16-17 as well is the owner's call (checklist).
- **Teacher Approved / Designed for Families:** not applicable.
