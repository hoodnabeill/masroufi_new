# Google Play Console notes

Answers for the Play Console forms, matching what Masroufi v2 does as of this commit. Re-check them whenever a feature
that touches data, permissions or the network changes. This is not legal advice. Billing and the store listing text
come in Phase 13; the Plus lines below describe the plan, not shipped behaviour.

## Privacy policy URL

Play requires a public URL. The repository is public, so the simplest option is GitHub Pages on `docs/`:
publish `docs/legal/privacy-policy.md` and paste its URL into **App content → Privacy policy**. The same text ships
inside the app (Settings → Privacy and terms), in Arabic and English, and `docs/legal/*` is written from it.

## Data safety form

- **Does the app collect or share any of the required user data types?** No. Everything is stored only on the
  device. There is no account, no server, no analytics, no ads and no crash-reporting SDK.
- **Is data encrypted in transit?** Not applicable: the app itself never uses the network and does not declare the
  `INTERNET` permission. Backups are files the user saves, encrypted with the user's password.
- **Can users request deletion?** Yes: Profile → Delete all my data, Android's clear-storage, or uninstall.
- Google Play Billing (Phase 13) is handled by Google Play, not by the app's own network code. If you ever add
  analytics, crash reporting, accounts, ads or a server, this form must be updated first.

## Permissions

Declared: `POST_NOTIFICATIONS` (reminders the user turns on), `USE_BIOMETRIC` (the app lock), `VIBRATE`.
Not declared: `INTERNET`, `RECEIVE_SMS`, `READ_SMS`, contacts, storage, location.

## SMS permission declaration

Not needed. v2 never requests an SMS permission: the user pastes a bank or wallet message and the app reads it on
the device, never storing or sending the text. If a later version asks for incoming-SMS access ("read bank messages",
shown as "قريب" in Settings), this section and the Permissions Declaration Form must be filled in first.

## Subscriptions (Plus)

State in the listing and in the app: the price and period, that it renews automatically, that it is cancelled in
Google Play → Subscriptions, and that the free version stays usable without paying (09-PREMIUM, "Wording for stores
and legal"). The in-app terms already say this.

## Content rating and audience

Finance utility, no user-generated content shared with others, no ads. Target audience: 18+. The child screen is part
of a parent's phone, not an account for a child.
