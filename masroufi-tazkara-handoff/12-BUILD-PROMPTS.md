# Build prompts

Paste these one at a time into a new project that contains this folder. Finish and run each phase before the
next. Each prompt is written to stand alone.

Ground rules to keep in every phase (Prompt 0 sets them):
- The files in this folder are the spec. `screens/*.dc.html` are the visual truth: 1 px = 1 dp.
- One phase at a time. Each phase ends with something that runs and is committed.
- Never invent features, copy, numbers or colours. If something is missing or contradictory, ask.
- Offline first. No analytics, ads or tracking libraries. No account.
- Data migrations always keep existing data.
- Every screen ships with its loading skeleton, empty state, error state and pressed states.

---

## Prompt 0 — Read first, then set up

> Read every file in `masroufi-tazkara-handoff/` in this order: `00-README.md`, `01-PRODUCT.md`,
> `02-FEATURES.md`, `03-DESIGN-SYSTEM.md`, `11-DATA-AND-RULES.md`, then skim the rest and open the files in
> `screens/`. Do not write any code yet.
>
> Then tell me, in plain language: (1) the product in five lines, (2) the phases you will build in, (3) anything
> in the spec that is unclear or contradicts itself, (4) the stack you recommend for an offline-first Android app
> with Arabic right-to-left as the main language, and why. Wait for my answer before starting.
>
> After I agree: create the empty project, the build pipeline, and an app that opens to a blank lapis-night
> screen (`#0A1033`) with the app name. Commit it.

## Prompt 1 — Design system and components

> Build the design system from `03-DESIGN-SYSTEM.md` and nothing else yet: colour tokens, the two fonts (Lalezar
> and Alexandria, bundled in the app), type roles, radii, spacing, and these components: primary button,
> secondary button, text link, chip, segmented control, card, row, toggle, bottom sheet, floating tab bar with
> the yellow add button, progress bar, the ticket (with perforation, notches and holes), stub, envelope card,
> stamp, proverb card, and the mascot Qersh with his five moods (`13-ICON.md`).
>
> Icons: one family, 24 grid, 1.9 stroke, round caps; outline for unselected and the filled version for
> selected. Draw only the icons used in the screens.
>
> Add the press behaviour from `05-MOTION.md` ("Buttons and taps") and the haptic levels from `06-HAPTICS.md` to
> the components themselves so every later screen gets them for free. Honour "remove animations" and right-to-left.
>
> Make a gallery screen that shows every component in every state. I will check it against the mocks.

## Prompt 2 — Data and the rules

> Implement storage and the calculations in `11-DATA-AND-RULES.md`: pockets, entries, categories, the day
> ticket, closing a day, streak, levels, jar movements, round-ups, envelopes and recurring items. No screens.
>
> Write tests for every rule in "The daily ticket", "Closing the day", "Round-ups" and "Envelopes", including
> the edge cases: over the ticket, zero or negative spendable, payday today, income logged mid-day, an entry
> added to a closed day, a missed day. Money is whole piastres everywhere. Show me the test list and results.

## Prompt 3 — The daily loop

> Build `Main.dc.html`, `AddPunch.dc.html`, `CloseDay.dc.html` and `Proverb.dc.html` exactly as drawn, wired to
> the real data. Include all states listed for them in `04-SCREENS.md`.
>
> Then build the four authored moments from `05-MOTION.md`: the punch, the tear, the card flip and the new
> ticket, each with its haptic from `06-HAPTICS.md`. Use the copy from `07-COPY.md` word for word. Saving an
> entry must update the ticket before storage finishes (`10-PERFORMANCE.md`).
>
> Done means: I can open the app, log three expenses in two taps each, watch three holes punch, tear the ticket
> at night and get a proverb card.

## Prompt 4 — Onboarding and empty states

> Build the five onboarding steps in `08-ONBOARDING.md` (`Onboarding`, `ObPayday`, `ObMoney`, `ObFixed`,
> `ObTicket`), including the edge cases at the bottom of that file. It must end by opening the add sheet.
>
> Build every empty state in `07-COPY.md` with Qersh's illustration (`EmptyStates.dc.html`), and the loading
> skeleton for Home and the roll. Add the first-week cards from `08-ONBOARDING.md`, one at a time.

## Prompt 5 — The roll and pockets

> Build `Roll.dc.html` (search, month switch, filters, expanding days) and `Wallets.dc.html` (pockets, the cash
> counter, moving money, ATM withdrawal, top-up, pasting an SMS). Entry detail and edit reuse the add sheet.
> Deleting shows a 5-second undo instead of a confirmation.

## Prompt 6 — The plan

> Build `Envelopes.dc.html`, `Jar.dc.html` and `Seasons.dc.html` with their detail and "new" screens (see the
> table at the end of `04-SCREENS.md`), recurring items and installments, the salary split, goals, the gold
> view and the zakat calculator. Follow the seasons and gold rules exactly: Hijri dates shown as approximate,
> gold price typed by the user and never presented as a market price.

## Prompt 7 — People

> Build `People.dc.html` and `Gameya.dc.html`: loans, the tab, quick split, the duty ledger and the gameya
> wheel, with reminders sent through the phone's own messaging apps. The app records only; say so where the
> mock does.

## Prompt 8 — Match, before you buy, basket, album

> Build `Match.dc.html`, `BeforeBuy.dc.html`, `Basket.dc.html` and `Album.dc.html` with the rules in
> `11-DATA-AND-RULES.md`. Add the stamps and levels. The match goal animation and the wishlist's 48-hour
> notification are in `05-MOTION.md` and `07-COPY.md`.

## Prompt 9 — End-of-month mode

> Build `LastDays.dc.html` and its triggers. It is free forever and the paywall never appears while it is on.

## Prompt 9a — Installments and freelancer mode

> Build `Installments.dc.html` and `Freelance.dc.html` with their rules in `11-DATA-AND-RULES.md` (sections
> "Installments" and "Freelancer mode"). Installments are fixed costs reserved on payday. In freelancer mode
> work income goes to the vault and the self-salary runs the payday ritual. Suggestions (early payoff, raise,
> lower) are only ever suggestions with two answers. Write unit tests for buffer months, freedom date, ticket
> growth, and the vault-short-on-payday case.

## Prompt 9b — Monthly story and kids' allowance

> Build `Story.dc.html` (all five slides, the short version for thin months, sharing with and without amounts)
> and `Kids.dc.html` with the child screen, advances, chores, the matched jar and handover. Follow the rules in
> `11-DATA-AND-RULES.md` and the motion and haptics added for them in `05-MOTION.md` and `06-HAPTICS.md`. The
> child screen must not be leavable without the parent's PIN or fingerprint. Do not build the shared home.

## Prompt 10 — Plus and the paywall

> Read `09-PREMIUM.md` fully. Build the trial, the entitlement checks for each Plus feature, Google Play
> billing with two products (yearly, monthly), and `Paywall.dc.html`. The loss rows must be built from the
> user's real data, show only rows with something in them, and follow every honesty rule in that file. Prices
> come from the store, not from text. Nothing the user entered is ever deleted when Plus ends.

## Prompt 11 — Trust

> Build `Settings.dc.html`, `Lock.dc.html` and `Backup.dc.html` with the "Lock" and "Backup" rules in
> `11-DATA-AND-RULES.md`: app lock (PIN and fingerprint, waiting time after wrong tries), hide amounts,
> encrypted backup and restore through the system file picker, weekly automatic backup, export to Excel, the
> daily reminder, the home-screen widgets, and Arabic and English with a language switch. Add privacy policy
> and terms screens.

## Prompt 12 — Polish pass

> Go through every screen with the checklist at the end of `10-PERFORMANCE.md`. Check: pressed state on every
> tappable thing, skeletons, empty states, optimistic updates, undo, 200% font size, TalkBack reading order,
> right-to-left, "remove animations", contrast of all text, 48 dp touch targets. Fix what fails and list what
> you changed.

## Prompt 13 — Store readiness

> Prepare the release: the icon from `13-ICON.md` in all required sizes including the themed version, the
> splash, signing, the store listing text in Arabic and English, screenshots from real screens with sample data
> marked as sample, the data-safety answers (no data collected, nothing shared), and the SMS permission
> declaration if auto-reading is included.

---

## Prompts for later changes

- **A new screen:** "Build `<name>` from the system in `03-DESIGN-SYSTEM.md`. Reuse existing components. Give
  it loading, empty and error states and the copy in `07-COPY.md`'s voice. Do not add new colours, fonts or
  icon styles."
- **A motion review:** "Play each authored moment in `05-MOTION.md` slowly and compare it with its description.
  List differences. Remove any animation that is not in that file."
- **A copy review:** "Find every user-facing string that breaks the voice rules in `07-COPY.md` (system words,
  bare statuses, blaming) and propose replacements."
