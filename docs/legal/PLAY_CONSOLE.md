# Google Play Console notes

Answers for the Play Console forms, matching what the app does as of this commit. Re-check them whenever a feature
that touches data, permissions or the network changes. This is not legal advice.

## Privacy policy URL

Play requires a public URL. The repository is public, so the simplest option is GitHub Pages on `docs/`:
publish `docs/legal/privacy-policy.md` and paste its URL into **App content → Privacy policy**. The same text ships
inside the app (More → Privacy policy).

## Data safety form

- **Does the app collect or share any of the required user data types?** No. All data is stored only on the device.
  Data that leaves the device does so only at the user's request and to a destination the user chooses (their own
  encrypted backup file, their own WebDAV server, apps they share to). Google's guidance treats on-device processing
  and user-initiated transfers to the user's own destinations as not "collected".
- **Is data encrypted in transit?** Yes (WebDAV over HTTPS is required for sync; backups are encrypted at rest).
- **Can users request deletion?** Yes: delete entries in the app, clear storage, or uninstall.
- If you ever add analytics, crash reporting, accounts, ads or a server, this form must be updated first.

## SMS permission declaration (Permissions Declaration Form)

The app requests `RECEIVE_SMS` only, and only when the user turns on "Read wallet and bank messages".

- **Core functionality / permitted use:** "SMS-based money management — apps that track and manage budget"
  (Google Play SMS and Call Log policy, Exceptions table).
- **Why no alternative works:** Egyptian mobile wallets (Vodafone Cash, e& cash, Orange Cash, WE Pay), InstaPay and
  local banks notify transactions only by SMS; there is no public API to read the user's own transaction history.
- **What is read:** incoming messages are checked on the device; only messages that contain a currency amount from a
  wallet, InstaPay or a bank are parsed (amount, type, counterparty, reported balance) to suggest an entry the user
  confirms. Message text is not stored or transmitted. SMS history is never read (`READ_SMS` is not requested).
- **Demo video:** record Settings → toggle on → permission prompt → incoming wallet SMS → notification → confirm in
  the quick-add sheet.

## Content rating and audience

Finance utility, no user-generated content shared with others, no ads. Target audience: 18+.
