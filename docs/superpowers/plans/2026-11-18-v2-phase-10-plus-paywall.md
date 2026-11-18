# Masroufi v2 Phase 10 (Plus and the paywall) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** The 14-day trial starting on day 7, real entitlement state for every Plus feature, the paywall built from the user's own numbers with every honesty rule, the safety ticket, and billing behind an interface — stubbed, because there is no Play Console account yet (owner decision).

**Spec:** `12-BUILD-PROMPTS.md` Prompt 10; `09-PREMIUM.md` (all of it); `02-FEATURES.md` "Free vs Plus" and §22 (safety ticket); `11-DATA-AND-RULES.md` ("Streak, levels, stamps", "The daily ticket"); mock `screens/Paywall.dc.html`; `04-SCREENS.md` "Paywall"; `05-MOTION.md` / `09-PREMIUM.md` "Motion and haptics"; `07-COPY.md`.

## Global constraints

As Phases 3–9b (Migrations only; strings ar + en; copy verbatim; Long piastres; injected Clock; the test rules in the carry-forward; poll CI at most once a minute; never push while your own CI run is in progress).

## Decisions taken in this plan (owner may override)

1. **Billing is an interface** (`Billing`: products with store-formatted prices, purchase(plan), restore(), active subscription with plan and renewal date, manage-subscription intent). This phase ships `StubBilling`: release builds report "prices not available yet" (the paywall says so plainly and the button is disabled — no hard-coded prices ever shown in release); debug builds return the mock's two prices clearly marked "(debug)" and a purchase that succeeds or cancels from the gallery, so the whole flow can be exercised. The Google Play Billing implementation (two products, yearly and monthly) is a Phase 13 store-readiness task, when the Play Console exists.
2. **Entitlement** = an active subscription, or an active trial, or (debug) the gallery override. `Entitlements.isPlus` becomes real in release (replaces `ReleaseEntitlements`' constant false). Nothing is ever deleted when Plus ends; existing Plus-locked screens already keep the data.
3. **Trial:** offered from day 7 of use (`firstOpenDay` + 6) with one honest Home card (what is in it, the end date, one tap to start, no payment method); 14 days; settings keys for start/end. Three days before the end: one quiet line on Home. The paywall opens by itself the day before the end and once on the end day; after that only from Settings or a Plus feature. It also never opens by itself during end-of-month mode, during onboarding, on the day after an overspent day, or more than once a day.
4. **Loss rows** (decision per `09-PREMIUM`): built from real data — gold grams of the jar (with a gold price), round-ups collected this month, seasons planned (count, nearest, saved), duty-ledger names (count, next occasion), streak days kept by the safety ticket, plus the basket (tracked items) and envelopes beyond four when present. Only rows with a value above zero; ordered by how much the user used each during the trial (records/events in the trial window); at most five. None → the plain version ("جرّبت المجاني أسبوعين. دي الحاجات اللي ممكن تزيد عليه") with three features. Opened from a Plus feature: that feature's row first, described with the user's data ("حصّالتك 1,840. بالدهب تبقى 0.37 جرام.").
5. **Safety ticket** (Plus): one missed (not closed by hand) day per Saturday–Friday week does not break the streak; the album/streak shows it; the paywall's row counts the streak days it protected.
6. **Every "جرّب بلس"** (the `PlusLocked` card, the envelope-limit sheet, the second gameya / second child, match history, basket, installment comparison) opens the paywall for that feature instead of the "بلس قريب" placeholder. All of them hide the button in end-of-month mode (Task 46 left the envelope-limit sheet out).
7. **Settings row** ("مصروفي بلس" with plan, renewal date, "إدارة الاشتراك", "استرجاع الاشتراك") is built as a component now; Phase 11's Settings screen places it.
8. **SMS auto-reading** (Plus) waits for the owner's decision on declaring the SMS permission on Play (open owner decision); not built.

---

## Task 52: Plus data — billing interface, entitlement, trial, loss rows, safety ticket

- `Billing` interface + `StubBilling` (decision 1) with Hilt bindings per build type; `PlusRepository` combining billing, trial and the debug override into `Entitlements.isPlus` (decision 2); restore on start.
- Trial state machine (decision 3): eligible/offered/active/ending soon/ended, start, the Home line and card data, the paywall auto-open rule (`shouldAutoOpenPaywall(today)` with every exclusion and the once-a-day cap stored in settings).
- `LossRows` builder (decision 4) incl. the plain version and the feature-first variant; every number from repositories, never invented.
- Safety ticket in `ProgressRules.streak` + album data (decision 5).
- Tests per rule: trial day 7 offer, 14-day end, 3-day line, auto-open the day before and on the day only, never during EOM / onboarding / after an overspent day / twice a day; entitlement sources; loss rows only above zero, ordering, max five, plain version, feature-first; safety ticket keeps a streak with one missed day per week and not two; nothing deleted when Plus ends (a Plus feature's data still readable after the trial).

## Task 53: The paywall and Plus wiring

- Paywall per `Paywall.dc.html`: trial-ending tag, sad Qersh, the headline, the loss rows (arriving 60 ms apart with dashed carnelian outlines), the always-visible data line ("بياناتك كلها هتفضل محفوظة ومش هتتمسح. اللي هيقف هو المزايا دي بس."), two plans from `Billing` (yearly preselected, "وفّر N%" and the per-month line computed from the store prices; monthly "تتجدد كل شهر"), one button "خلّي حاجتي معايا", the renew/cancel line naming Google Play, the close button and "لا شكرًا، كمّل بالمجاني" (both ≥ 44 dp, closing never asks). Prices unavailable → a plain line and the button disabled. Purchase: Tick on plan select, Confirm on the button, success → Milestone, Qersh delighted, outlines solid turquoise, close after 900 ms to where the user came from; cancel/fail → "الدفع ماكملش ومتخصمش منك حاجة." No countdowns, no fake discounts.
- Home: the trial card (decision 3) in the card slot rules, the 3-day line, the auto-open.
- Decision 6 wiring everywhere; the Settings Plus row component (decision 7); the gallery's debug purchase controls.
- Tests: ViewModel tests for rows shown, plans and the computed savings line from store prices, unavailable prices, purchase success/cancel states, close paths, auto-open conditions on Home, every Plus entry point opens the paywall with its feature first, button hidden in the mode.
