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

- Add `DayTicketDao.forDateFlow(date)` so Home can observe today's row.
- Decide whether a transfer fee punches today's ticket (today it does not; `isFee` is never written). Decide before the add sheet's "رسوم" field.
- Decide what an end-of-month row stores (pool and days left are not saved today) before the schema is frozen.
- Commit `app/schemas/com.masroufi.app.data.db.TazkaraDatabase/1.json` from the CI artifact once Phase 3's schema is final; every later change needs a Migration.
- Wire WorkManager for the midnight print: remove the default initializer and implement `Configuration.Provider` with the Hilt worker factory.
- Show the ticket only after its row has loaded (avoids a punch animation on every open).
- `edit` rejects any change to an entry whose pocket was archived later; the UI must explain it.
- Check the touch-down press delay inside scrolling lists.
- The gallery is the start screen until Home replaces it.

## Later phases

- Phase 6: reset `paidThisCycle` at payday; touch it only for entries in the current cycle (a backdated or old-cycle payment can mark/unmark the current cycle today); reserve bills only from counting EGP pockets; round-up netting assumes the jar total ≥ 0 — revisit with jar withdrawals.
- Phase 11: in-app font licences; rewrite `docs/legal/*` (still describes v1's WebDAV/SMS/Internet); delete v1's old database and `settings` file on first launch or in "delete all my data"; English copy review.
- Anytime: one constant for "EGP" in SQL; named type styles for the few inline font sizes; release lint pass after the AGP upgrade; Odometer leading-digit roll; small test gaps listed in review notes.

## Open owner decisions from the handoff (unchanged)

Plus prices, trial length, whether gold price is ever fetched, the last 10 proverbs, kids' allowance limits, and
whether the SMS permission will be declared on Play.
