# Masroufi v2 "Tazkara": design

Date: 2026-11-13. Branch: `v2`.

## Intent

Rebuild Masroufi from the ground up as the "Tazkara" product described in `masroufi-tazkara-handoff/`. Every day
is a ticket showing one number: what the user can spend today and still reach payday. Expenses punch holes, the
day is torn off at night, the leftover goes to the jar and a proverb card is won. The handoff folder is the
product spec; this document records how it gets built in this repository and the calls made where the handoff
is silent or contradicts itself.

## Goals

- Ship every Free and Plus feature in `02-FEATURES.md` and every screen in `04-SCREENS.md`, phase by phase, in
  the order of `12-BUILD-PROMPTS.md` (Prompts 0 to 13, with 9a and 9b).
- Match the mocks in `screens/*.dc.html` (1 px = 1 dp), the words in `07-COPY.md`, the motion in
  `05-MOTION.md` and the haptics in `06-HAPTICS.md`.
- Every rule in `11-DATA-AND-RULES.md` is implemented in pure, unit-tested Kotlin.

## Non-goals

- The shared home (بيتنا). Not designed; not built.
- Importing v1 data. v2 starts empty (owner decision, 2026-11-13).
- Any network feature: no sync server, no WebDAV, no price fetching, no analytics, no ads.

## Anti-goals (failure even if it works)

- Invented numbers, statistics, testimonials or market prices on screen.
- Shaming copy, red alarms, fake scarcity, countdown timers on the paywall.
- Anything protecting a user in a bad month (end-of-month mode, lock, backup) behind Plus.
- Spinners, disabled-while-saving buttons, or a screen that appears empty and then fills.

## Constraints

- Android only, native, offline-first. Arabic (Egyptian colloquial) first, English second, RTL, Western digits,
  dark only.
- Money is `Long` piastres everywhere. One primary currency (EGP).
- Schema changes after the first release migrate data; never wipe.
- No JDK, Android SDK or `gh` on the owner's PC. CI (GitHub Actions) is the only build.

## Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (picked)** | Replace v1 on branch `v2`: delete v1 sources, build fresh under the same app ID `com.masroufi.app`, new database file `tazkara.db`, port only the v1 pieces that already meet the spec | A v2 build installed on a phone replaces v1 there |
| B | Same as A with a temporary app ID so v1 and v2 sit side by side | Must switch the ID back before release; testers reinstall |
| C | Evolve v1 in place | v1's transaction/budget model and liquid-glass look fight the ticket model and flat paper design; more undoing than building |

The owner picked A because the request was "up from the ground" and v1 data does not need to survive.

## Decisions

### Repository
- Branch `v2` from `main`. `main` keeps v1 untouched until v2 is ready to merge.
- `masroufi-tazkara-handoff/` is committed on `v2` as the spec. The `.zip` copy is not committed (identical).
- v1 `app/src`, v1 `README.md`/`PRODUCT.md` content and `masroufi-v2-design/` (the abandoned "Midnight Ledger"
  redesign) are removed on `v2`. `docs/legal` is kept and rewritten in Phase 11. `marketing/` stays untracked.

### Stack (same as v1, which the handoff recommends)
- Kotlin 2.2, Jetpack Compose (BOM 2025.10.01), Room 2.7, Hilt, Navigation Compose (type-safe routes),
  DataStore, WorkManager, BiometricPrompt, Glance (widgets), kotlinx.serialization, Play Billing (Phase 10 only).
- Dropped from v1: OkHttp (no network), Coil (no images to load), Kyant Backdrop (no glass),
  material-icons-extended (one custom icon family).
- minSdk 26 (gives `java.time.chrono.HijrahDate` for seasons), target/compile 35, single `:app` module.
- Fonts Lalezar and Alexandria (SIL OFL) bundled in `res/font`, licences in `licenses/`.

### Ported from v1 (reviewed, adapted, re-tested; not copied blind)
- `security/Crypto.kt`: AES-256-GCM with PBKDF2-HMAC-SHA256 key stretching for backups; salted PBKDF2 PIN hash.
- `security/AppLockManager.kt` back-off logic, adjusted to the spec (5 wrong = 30 s, doubling, cap 15 min;
  60 s background timeout).
- CI workflow and the fixed debug signing key `app/signing/ci-debug.p12`, so v2 builds install over each other.

### Architecture
- Packages: `core` (money, dates, pay cycle), `domain` (pure rules: ticket, close day, streak, levels, round-up,
  envelopes, end-of-month, match, before-you-buy, freelancer, installments, kids, story), `data` (Room entities,
  DAOs, repositories), `ui/theme` + `ui/components` (design system), `feature/*` (one ViewModel per screen,
  `StateFlow` state), `work` (midnight ticket, reminders, auto backup, recurring items), `widget` (Glance).
- A stored `DaySummary` row per day is the single source for Home and widgets (`10-PERFORMANCE.md`): updated on
  every entry change, never recomputed from history on open.
- Optimistic UI: ViewModels update state first, write in the background, roll back on failure.

### Phases
Exactly the handoff's prompts, one commit (or a few) per phase, each phase building and passing tests before the
next starts: 0 setup, 1 design system and gallery, 2 data and rules, 3 daily loop, 4 onboarding and empty states,
5 roll and pockets, 6 plan (envelopes, jar, seasons, recurring, installments rows, zakat), 7 people and gameya,
8 match, before-you-buy, basket, album, 9 end-of-month, 9a installments and freelancer, 9b story and kids,
10 Plus and paywall, 11 settings, lock, backup, widgets, languages, legal, 12 polish, 13 store readiness.

### Builds
- CI only (owner decision, 2026-11-13): no local JDK or SDK. The workflow's `push` trigger gains the `v2`
  branch; each phase is pushed to `origin/v2` and is done only when that CI run is green (unit tests plus debug
  and release APKs). Compile errors are fixed from the CI log, so phases are kept to sizes one or two CI round
  trips can settle.
- Fonts: Lalezar and Alexandria TTFs downloaded from the official `google/fonts` GitHub repository (owner OK).
- English: the builder translates every string in Qersh's voice in Phase 11; the owner reviews before release.
- Billing: no Play Console or release key yet. Phase 10 builds the trial, entitlements and paywall against a
  billing interface with a stub implementation (owner decision). Real Play Billing is wired once the owner has
  the account, key and two products.

## Calls made where the handoff is silent or contradicts itself (builder's calls, owner may override)

1. **Installment and fixed-bill entries never punch the ticket.** `11` says installments "never punch a day" but
   also that each creates an entry on its day. The entry is created with its recurring link and is excluded from
   today's spent.
2. **The daily formula rules.** The payday ritual's "what is left ÷ days in the cycle" is shown as a preview;
   each morning's ticket still uses (Spendable − Reserved) ÷ Days left, so overspends spread as specified.
3. **Two roundings for installments stay as written:** per-day cost rounds to nearest, ticket growth rounds down.
4. **Proverbs:** ship the 50 drafted cards; the set size is read from data, so adding the last 10 after native
   review is a data change only. Copy that says "60" reads the real count.
5. **Plus before Phase 10:** a single `Entitlements` gate exists from Phase 2; in debug builds a switch in the
   gallery turns Plus on and off. Prices on the paywall come only from Play; with no Play product configured the
   paywall says in plain words that Plus can't be bought yet, rather than showing hard-coded prices.
6. **Open owner decisions use the handoff's proposals** until changed: 14-day trial from day 7, gold price typed
   only, one free child, advance capped at half a week, yearly 399 / monthly 49 as Play product prices.
7. **SMS auto-read (Plus)** is built behind the `RECEIVE_SMS` permission in Phase 11 only if the owner confirms
   it will be declared on Play; paste-to-parse (Free) needs no permission and ships regardless.
8. **Starter ticket when money is skipped in onboarding:** 100 EGP until the user enters an amount.
9. **The whole jar is reserved.** The jar is not a pocket, so pocket balances do not drop when money goes into
   it. To keep saved money out of tomorrow's ticket, Reserved includes the entire jar total, not only
   goal-earmarked money; the emergency envelope's balance is reserved the same way. A round-up counts as spent
   today and adds to the jar, so tomorrow's Spendable − Reserved falls by the same amount. "Free jar money" (for
   early payoff) = jar total − money earmarked for goals. "صرفت نظر" is capped at today's Spendable − Reserved.
10. **Tickets exist only for days the app printed one.** A ticket prints on the first open of a day (or at
    midnight while running). If the app is not opened for several days, only the last printed, unclosed day
    auto-closes on the next open; the days in between have no ticket. They show as grey in the roll, count as
    "never torn" in the story, score nothing in the match and break the streak. No leftover is credited for
    them, so the jar never receives money the user may have spent without logging.
11. **"The month" is the pay cycle** (payday to the day before the next payday; rolling 30 days from the last
    income when not fixed; self-payday to self-payday in freelancer mode) for envelopes, end-of-month mode,
    the payday ritual and the monthly story. The only calendar-month figure is a pocket's "moved this month"
    against its provider limit, as `11` says. One `PayCycle` type in `core` defines it.
12. **Screens without their own build prompt** are placed: Subscriptions and Payday ritual in Phase 6 (with
    recurring items), Wallet detail in Phase 5 (with pockets), Profile in Phase 8 (with the album).

13. **A transfer or cash-out fee punches today's ticket** (owner decision, 2026-11-13): the fee counts in today's
    spent and makes one hole; the transferred amount itself never punches.

## Left to the builder

Package and class naming, Room schema shape within the "What to store" table, the icon vector paths (drawn to
`03`/`13`), test layout, and splitting a phase into several commits.
