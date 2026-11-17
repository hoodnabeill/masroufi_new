# v2 foundation (Phases 0–2): rulings and carry-forward

Branch `v2`, commits 008bf66..HEAD. Every task passed a dual review (the "Santa loop") with green CI. This file keeps
the decisions made during the build and what the next phases must pick up.

## Rulings made during the build (owner may override)

| # | Ruling | Cost if wrong |
|---|---|---|
| 1 | Review gate: two independent Claude reviewers per task (no external model installed), max 3 fix rounds | Extra rounds, or a parked finding |
| 2 | No local JDK: the green CI run is the test evidence; branches are pushed before review | Unreviewed code briefly on origin/v2 |
| 3 | Lalezar has no tabular digits; `Odometer` lays each digit in a fixed cell as wide as "0" | Digits look slightly wider than drawn |
| 4 | With system "remove animations", motion is instant (Compose scale 0), not a 120 ms fade | No fade on reduced-motion phones |
| 5 | `TzIcon` takes a `TzIconDef` (to pick the 1.9/2.2 stroke) | None |
| 6 | Primary/secondary buttons and links fire no haptic of their own; the caller fires the action's haptic | A caller that forgets is silent |
| 7 | Filter chip 40 dp, choice chip 44 dp (mocks win over the brief) | None |
| 8 | The ticket punches its newest hole whenever `holes` increases (no separate state object) | Home must only show the ticket once its row has loaded |
| 9 | Ticket `label` is the header line; a separate `amountLabel` holds "معاك النهارده" | One more parameter |
| 10 | Qersh's coin ring only above 64 dp (mocks) | None |
| 11 | Ticket shows 0, never a negative number; envelope `status`/`sealedLabel`, stamp `subLabel` added | None |
| 12 | Not-fixed pay cycle: anchored at the last income, always contains today (future income becomes the next "payday") | None for past dates |
| 13 | `computeTicket`: end-of-month only when Spendable − Reserved ≤ 0, or base + carry = 0; carry capped at what is available | Small change to the rule and its tests |
| 14 | "Spread over the remaining days" uses days after today; on the cycle's last day the line is skipped (Phase 3) | — |
| 15 | No foreign keys (pockets are archived, never deleted); a balance counts fees off the source pocket | A stray id silently drops an entry from totals |
| 16 | Debug builds have a "Plus (debug)" switch in the gallery (`setDebugOverride`) | None |
| 17 | Reserved = unpaid bills due before payday + jar + emergency pot (each clamped at 0) | — |
| 18 | A forgotten day's "tomorrow" leftover only carries when it is yesterday; otherwise it goes to the jar | — |
| 19 | 50 proverbs for now (`PROVERB_SET_SIZE`); read the real count once the texts exist | — |
| 20 | Editing an entry while round-ups are off keeps its old round-up (spec: "turning off keeps old ones") | — |
| 21 | Logging a bill payment marks the bill paid this cycle; deleting it un-marks when nothing else pays it | A bill can show paid early |
| 22 | v2 uses its own settings file (`tazkara_settings`) and database (`tazkara.db`); v1 files are left untouched | v1's files stay on upgraded phones until deleted |

## Fix at the start of Phase 3 (Home, add sheet, close day)

- ~~Add `DayTicketDao.forDateFlow(date)` so Home can observe today's row.~~ DONE (Phase 3).
- ~~Decide whether a transfer fee punches today's ticket.~~ DONE: it does (one hole, adds to spent).
- ~~Decide what an end-of-month row stores.~~ DONE: `eomPool` and `eomDaysLeft` are stored on the row.
- Commit `app/schemas/com.masroufi.app.data.db.TazkaraDatabase/1.json` from the CI artifact: NOT done, see "Phase 3 notes".
- Wire WorkManager for the midnight print: remove the default initializer and implement `Configuration.Provider` with the Hilt worker factory.
- ~~Show the ticket only after its row has loaded.~~ DONE (Home composes the ticket only when Ready).
- `edit` rejects any change to an entry whose pocket was archived later; the UI must explain it.
- Check the touch-down press delay inside scrolling lists: noted, not measured on a device yet.
- ~~The gallery is the start screen until Home replaces it.~~ DONE: Home is the start screen; the gallery is reached from the Plan tab (debug).

## Phase 3 notes (daily loop: Home, add sheet, close day, reward)

Decisions made during Phase 3 (owner may override):

- The payday line "هيفضل معاك حوالي Y يوم 25": Y = morning spendable − unpaid bills before payday − carry − today's base × days left. Jar and emergency money count as still yours.
- Category icons: فواتير = bell, صحة = grid (no closer icon exists yet); seeded names and "الكاش" are stored in Arabic.
- Proverb card colours by group: saving and patience turquoise, spending yellow, debt carnelian, work sky, people lilac.
- One card per close: a rare card (45 first full week, 50 thirty-day streak) replaces that day's ordinary card; both rare in one day → 45 now, 50 next close.
- Tomorrow's preview uses tomorrow's own pay cycle (fixes the day before payday).
- Saves show on Home at once through `PendingPunches`; a failed save rolls back with "ماتسجّلتش. جرّب تاني."
- Quick log and repeat chips log to the pocket of the chip / the lowest-id counting pocket.
- The reward screen is skipped (back to Home) for a day that closed itself or has no card.
- Reviews: from Task 15 on, tasks shipped on a green CI run without the two-reviewer loop (owner instruction, 2026-11-17).

- Guessed copy to review (written by the builder, not in `07-COPY.md`): the TalkBack label of the streak; plural forms such as "في 1 مرات"; the transfer wording (حوّل / إلى / في / "من {from} لـ {to}"); the leftover destinations لبكره / للطوارئ / نادر; the share-failure toast; every English string in `values-en/`.
- The add sheet rises from the bottom of the screen, not out of the + button.
- `app/schemas/.../1.json` is still to be committed once a build is installed on a real phone (CI artifacts need a login to download); until then the comment in `TazkaraDatabase.kt` says schema v1 may change.
- Dev installs need an uninstall and reinstall after Phase 3's v1 schema column changes (no migration exists yet).
- New-ticket moment: `lastTicketShown` (epoch day) lives in `tazkara_settings`; it is written when the three steps end, so leaving Home mid-moment replays it.

## Phase 6 notes (plan tab)

- Bill model: each payment advances `nextDue` one period; deleting a payment moves it back one period; a bill is reserved while `nextDue` is before the next payday (unpaid overdue bills stay reserved). `paidThisCycle` is unused and the payday reset was removed (supersedes ruling 21 and the Phase 6 "Later phases" item).
- Payday ritual "where it went" bar follows the mock colours (jar turquoise, tickets yellow), not the brief's text.
- The roll shows "وزّع المرتب" under income rows on non-ticket days (always visible; tapping the row still opens the editor).
- Skipping the ritual marks that income as offered; the roll link can still reopen it.
- The fixed-costs step is numbered 2 until Phase 7 adds the debts step.

## Later phases

- Phase 6: reset `paidThisCycle` at payday; touch it only for entries in the current cycle (a backdated or old-cycle payment can mark/unmark the current cycle today); reserve bills only from counting EGP pockets; round-up netting assumes the jar total ≥ 0 — revisit with jar withdrawals.
- Phase 11: in-app font licences; rewrite `docs/legal/*` (still describes v1's WebDAV/SMS/Internet); delete v1's old database and `settings` file on first launch or in "delete all my data"; English copy review.
- Anytime: one constant for "EGP" in SQL; named type styles for the few inline font sizes; release lint pass after the AGP upgrade; Odometer leading-digit roll; small test gaps listed in review notes.

## Open owner decisions from the handoff (unchanged)

Plus prices, trial length, whether gold price is ever fetched, the last 10 proverbs, kids' allowance limits, and
whether the SMS permission will be declared on Play.
