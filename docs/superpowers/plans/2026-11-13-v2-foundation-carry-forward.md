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

## Phase 7 notes (people)

- `debt.amount` is what is still open; a partial repayment lowers it, a full one sets `settled`; the history is the DEBT entries linked by `debtId`. A split's one DEBT entry has `debtId` null (it covers several debts).
- Tab items count on the "I owe" side (totals and per-person balance) and are also reported apart as "on the tab".
- The user is not a `gameya_member` row: participants = members + 1; a `gameya_payment` with `memberId` null is the user's own.
- Shared-subscription debts go to a person named "{sub} · {N−1} صحاب" (found or created by name); editing or deleting the payment re-syncs the debt only while it is unsettled and unchanged.
- DEBT counts as moved money for a pocket's monthly limit; ADJUST does not.
- Deleting a person deletes their debts, tab items and gifts; money entries stay, unlinked.
- The gameya statement text and the shared-subscription person name are Arabic literals in data code (no Context in repositories).
- The ritual preview does not check that the pocket covers the ticked repayments.
- Task 37: the payday debts step lists loans only (not tab items) and ticks them all by default, as drawn; the duty ledger and gameya routes are stubs (`SoonScreen`) for Tasks 38 and 39; the reminder feedback shows when the share sheet closes (Android cannot say whether anything was sent).
- Task 37 split: equal shares over everyone ticked (incl. the user); a typed share is fixed and the rest split equally; unticking the user puts the whole bill on the others. A row's "سدّد" repays all that person's open loans in full (partial amounts per debt in person detail). Due date is a chip (none / 1 week / 2 weeks / 1 month). A repayment with no pocket leaves no history row.
- Task 38 duty ledger: no schema change; a personal season matches a "theirs" occasion with the same trimmed name and a person set (renaming either side breaks the link); the season note uses the person's name, not a relation word; a "theirs" occasion with no person never gets a suggestion; date picker is month chips + day/year steppers; all duty-ledger copy is the builder's (07-COPY has none).
- Task 39 gameya: the user's seat is moved in the form's turn list (no separate "my turn" stepper); edit changes name and monthly only (turns via swap; members fixed after creation); swap lists members not yet paid out; installment due day is the repo default (day 1); plan-row season/goal link not exposed; the statement text stays an Arabic literal (English users get Arabic); the pay sheet defaults to the first pocket; `SoonScreen` is unused.

## Phase 8 notes (match, buying, album, profile)

- Match: a closed day is any status but OPEN (auto-closed included); streak and stamps count hand-closed only. Week number = ISO week of the Monday inside the Saturday–Friday week; days to the whistle count today. Hat-trick nudge when the two days before today were goals and today is open.
- Stamps: first gram is checked on jar deposit, gold-price save and close day (not on round-ups); settled needs at least one debt ever and none open. Existing users see their earned levels/stamps as "new" once after the update.
- Birthday plan rounds up to the piastre (1,000 over 122 days = 8.20/day; the mock shows 8).
- Household scaling: factor = household size ÷ 4; school = per-child base × children in school; both bases are 0 today, so nothing changes on screen until real defaults exist. `SeasonRepository.seedDefaults` is not scaled; the UI uses `ProfileRepository.schoolSuggestion`.
- Home card order follows the main goal; the people and seasons keys are ignored until Home has those cards.
- DataWipe clears Room, resets `tazkara_settings`, deletes v1's `masroufi.db` and `datastore/settings.preferences_pb` (closes the Phase 11 v1-file item).
- Task 41: the first priced entry creates a hidden `tracked_item` row; it is listed after two priced entries or when the user adds it; tracking runs for everyone, only basket reads are Plus. "زوّد التذكرة" stores `ticketRaise` until the next payday and lifts the printed base (today's open row at once); tomorrow's preview does not show the raise yet (Task 44). The suggested ticket follows the rule (240 × 1.09 = 261), not the mock's 252. "صرفت نظر" caps the jar move at spendable. 0.5 ticket is spoken "نص". The unit price uses the system keyboard; editing an entry keeps but does not show its item and unit price.

- Task 42: a "last-minute" goal is a leftover of 1–10 pounds; exactly 0 reads "جون! قفلت على الحد بالظبط"; best day needs two goals with a leftover. Only a goal for flips the score; one haptic per visit. First week hides the scoreboard and league. Locked league weeks show a count over placeholder rows (no real data behind the blur). The album header counts N of 50 (the catalog size), not the mock's 60; the progress bar shows progress inside the current level. Milestones celebrate once in the album (first unseen item), not on Home. Basket, Profile and AppSettings are stub routes until Task 44 / Phase 11.

- Task 43: DB v6 (`MIGRATION_5_6`, `wish.movedToJar`) so a wish row shows the real amount moved to the jar. A wish is marked bought only after its prefilled entry saves (cancel keeps it waiting). The Home 48-hour card takes the first-week card's slot (a first-week card wins; hidden in end-of-month mode), outside `cardOrder`. Installment months 1–120. English tickets tile shows "7.5" ("ونص" is Arabic only). Before you buy is the third Plan-tab link.

- Task 44: the basket suggestion is held back while an accepted raise holds and after "سيبها" for the month; no sparkline (no price series stored); the mock's delivery habit fact is not built (no category data). Birthday gift is whole pounds rounded up (1,000 over 117 days = 9). Household steppers save only on a tap; work kind and main goal toggle off when tapped again. Share is plain text. "Delete all my data" relaunches MainActivity in a cleared task and kills the process (not verified on a device). The school-season default stays 0 (no invented amounts). Guessed copy: "رخصت عليك", "ثابتة", the suggestion body.
- CI (Phase 8): the unit-test step stops after 20 minutes; the summary names a hung test (last STARTED without a result) and adds a thread dump of the test JVM taken at 10 minutes. A test must never `runBlocking` a DataStore read on the main thread while a ViewModel edit may be in flight: the edit's transform runs on the test's main dispatcher, so they deadlock (read through `io` while pumping the dispatcher). `listOf(0, 0L)` infers every literal as Long.

## Phase 9 notes (end-of-month mode)

- Task 45: in the mode the default leftover destination is the existing `TOMORROW` and the ticket ignores the carry (the pool already holds it); the mode beats the basket raise; switching on/off re-prints today's OPEN ticket; `eomSince` keeps earlier auto-closed days on the normal default; switching on clears paused habits and no-spend dates; auto-off in `printToday` when payday comes or the cycle's next payday changes. A day with a bill payment is not a no-spend day. Offer = today under 60% of the cycle's earlier non-EOM printed tickets (3+). `isOn` does not re-emit at midnight by itself. Guessed copy: the paused-habit line.

## Later phases

- Phase 6: reset `paidThisCycle` at payday; touch it only for entries in the current cycle (a backdated or old-cycle payment can mark/unmark the current cycle today); reserve bills only from counting EGP pockets; round-up netting assumes the jar total ≥ 0 — revisit with jar withdrawals.
- Phase 11: in-app font licences; rewrite `docs/legal/*` (still describes v1's WebDAV/SMS/Internet); delete v1's old database and `settings` file on first launch or in "delete all my data"; English copy review.
- Anytime: one constant for "EGP" in SQL; named type styles for the few inline font sizes; release lint pass after the AGP upgrade; Odometer leading-digit roll; small test gaps listed in review notes.

## Open owner decisions from the handoff (unchanged)

Plus prices, trial length, whether gold price is ever fetched, the last 10 proverbs, kids' allowance limits, and
whether the SMS permission will be declared on Play.
