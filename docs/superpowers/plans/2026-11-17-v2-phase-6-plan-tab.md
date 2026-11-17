# Masroufi v2 Phase 6 (the plan tab) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** The Plan tab (الخطة) with its three views — envelopes (الظروف), seasons (المواسم, Plus), the jar (الحصّالة) — plus recurring fixed costs, subscriptions, goals, the gold view (Plus), round-ups settings, the zakat calculator (Plus) and the payday ritual (يوم القبض).

**Spec:** `02-FEATURES.md` §9–13, §23, §26; `11-DATA-AND-RULES.md` ("Envelopes", "The jar and gold", "Seasons", "Subscriptions", "Payday ritual", "Round-ups"); `04-SCREENS.md` ("Envelopes", "Jar", "Seasons", "Subscriptions", "Payday", "Screens not drawn"); `09-PREMIUM.md` ("Where Plus appears inside the app"); mocks `screens/Envelopes|Jar|Seasons|Subscriptions|Payday.dc.html`; `07-COPY.md`.

## Global constraints

As Phases 3–5 (incl. real Migrations, no destructive fallback; strings ar + en; copy verbatim; Long piastres; injected Clock). Free/Plus per `02-FEATURES.md` "Free vs Plus": envelopes 4 free, seasons + zakat + gold + round-ups Plus. Hijri dates via `java.time.chrono.HijrahDate` (minSdk 26) and always labelled "تقريبًا".

## Decisions taken in this plan (owner may override)

1. **Plus-locked screens** (seasons, zakat, gold card, round-ups) show the user's data blurred behind one card with what it would show and a "جرّب بلس" button (`09-PREMIUM.md`). Until Phase 10 builds the paywall, the button opens a placeholder sheet "بلس قريب" (builder's wording); debug builds use the gallery's Plus switch to unlock.
2. **Moving money between envelopes** changes this cycle's budgets only: an `envelope_move` row (cycle start, from, to, amount) adjusts both envelopes' "monthly" for the current cycle; next cycle starts from the saved amounts again.
3. **Season savings** are jar money earmarked for the season (a jar movement with `seasonId`), like goals (`goalId`). Earmarking moves nothing out of the jar; the jar total stays the same and stays reserved.
4. **Recurring fixed costs** get their own pushed list "الثوابت" (builder's wording; mock-less, built per "Screens not drawn") reached from the Plan tab, with add/edit/mark-paid. Paying from the list logs the entry (recurringId set) like any payment. At each payday (`printToday` crossing into a new cycle) every recurring item's `paidThisCycle` resets and `nextDue` advances by its schedule (closes the Phase 2 carry-forward).
5. **Payday ritual trigger:** a RECEIVED entry into a counting pocket of at least half of `usualSalary` (stored the first time the user completes the ritual; before that, any RECEIVED ≥ the sum of unpaid fixed costs) opens the ritual once; it can also be opened from the roll's salary row. Debts step shows only when debts exist (People is Phase 7, so hidden for now). "اطبع تذاكر الشهر" applies the jar share (MANUAL jar movement), marks the ticked fixed costs as reserved (they already are), saves the split, and re-prints today's ticket (deleting today's open row if it has no entries).
6. **Envelope limit:** creating a 5th envelope on Free shows the Plus card; existing envelopes are never hidden or deleted (`09-PREMIUM.md`).
7. **Zakat:** al-mal = 2.5% of zakatable money the user confirms (pockets + jar by default, editable) when above the nisab the user types or confirms (85 g gold × the user's gold price if set, else the user types the nisab); al-fitr = people count × the amount per person the user types. No fatwa text beyond the formula; the screen says the user confirms the nisab.

---

## Task 29: Plan data, part 1 — schema, envelopes, recurring

- Migration 2→3: tables `season` (id, kind, name, hijriMonth?, hijriDay?, gregorianDate?, target, color, updatedAt), `season_item` (id, seasonId, label, amount), `envelope_move` (id, cycleStart, fromId, toId, amount, at), `split_rule` (id, envelopeId, kind FIXED/PERCENT, value, sortOrder); columns: `jar_movement.seasonId`, `recurring.scheduleType` (MONTHLY default), `recurring.trialEnd`, `recurring.yearlyPrice`, `recurring.sharedWith` (Int, people count incl. user), `recurring.answers` (text, last three answers), `recurring.lastAskedAt`, `envelope.archived`. Migration test.
- `EnvelopeRepository`: observe envelopes with left (`envelopeLeft` using this cycle's spending in linked categories + moves), bar, honest line (`runsOutDay` / sealed "مدفوع" / "ماشي على المعدل" / "أحسن من الشهر اللي فات" — compare with last cycle), unassigned from the last split; create/update/archive (Free limit 4); `move(from, to, amount)`; `saveSplit(rules)`; `applySplit(income)` via `splitIncome`.
- `RecurringRepository`: CRUD, `markPaid(id, pocketId)` (logs the entry), payday reset + nextDue advance (called from `printToday` when the cycle changes; idempotent), list due before payday.
- Tests per rule.

## Task 30: Plan data, part 2 — jar, goals, seasons, zakat, subscriptions, payday

- `JarRepository`: total, this month's growth, sources (ticket leftovers / round-ups / manual, with counts), deposit/withdraw (MANUAL, withdraw capped at the free jar total), goals CRUD + earmark, gold (`settings.goldPricePerGram`, `goldPriceDate`; grams, "فاضل على أول جرام").
- `SeasonRepository`: default seasons (Ramadan, Eid al-Fitr, Eid al-Adha as Hijri; summer July, school September as Gregorian; personal occasions), next occurrence and days left (Hijri → Gregorian via `HijrahDate`), saved (earmarked jar), per day needed (ceil), checklist, "all seasons together" (Σ monthly need vs average monthly jar growth over 3 months).
- `domain/Zakat.kt` per decision 7.
- `SubscriptionRepository` (recurring kind SUBSCRIPTION): monthly total (yearly ÷ 12), tickets = total ÷ today's ticket (floor), per year, trial countdown, question of the month (longest since asked; two "not really" in a row → saving line monthly × 12), shared share, yearly hint.
- `PaydayRitual` domain/repo: inputs (salary, jar share option, fixed costs this cycle), the "where it went" bar, month ticket preview (`computeTicket` for the new cycle), apply.
- Tests per rule.

## Task 31: Plan tab and envelopes

- Plan tab replaces the placeholder: title "الخطة", Segmented (الظروف / المواسم / الحصّالة), and under it links to الاشتراكات and الثوابت.
- Envelopes view per `Envelopes.dc.html`: salary split summary ("مرتب نوفمبر اتوزّع كده", unassigned line), envelope grid (`EnvelopeCard`), "ظرف جديد: دروس، دكتور، لبس" dashed card, the move tip; drag an amount chip from one envelope to another (pick up Tap, drop Confirm, both numbers roll) and a "انقل من ظرف لظرف" sheet as the accessible alternative. Empty → "لسه مفيش ظروف" + "قسّملي" (creates 4 suggested envelopes from the seeded categories; household numbers scale only suggestions).
- Envelope detail (hero number, bar, entries of its categories as roll rows) and new/edit envelope (pushed, one question per block: name, colour, monthly, kind, categories).
- الثوابت list (decision 4).

## Task 32: Jar and goals (gold Plus)

- Per `Jar.dc.html`: jar drawing filling with coins (level = total vs largest goal or a fixed scale), total, "+X الشهر ده", the honesty line, the gold card (Plus; price edit sheet "عدّل السعر"), round-up card (Plus; toggle + step chips 5/10/50 writing `roundUpStep`), sources, goals ("هدف جديد", progress, honest ETA only after 30 days of history), the nearest season row, "فكّ منها" / "حطّ فيها" sheets. Empty → "الحصّالة مستنية أول قرش".

## Task 33: Seasons and zakat (Plus)

- Per `Seasons.dc.html` (Plus-locked per decision 1): intro, nearest season hero with checklist chips, timeline, "كل المواسم مع بعض" card with the honest gap and "رتّبهم حسب الأهم", zakat row → zakat calculator screen (inputs as rows, result as a yellow hero). New season / edit season (pushed). Personal occasions only (the duty-ledger link comes with Phase 7).

## Task 34: Subscriptions

- Per `Subscriptions.dc.html`: monthly total + tickets + yearly, free-trial card with "فكّرني يوم 15" (stores the reminder; notification Phase 11) and "هكمّل فيها", question of the month card with three answers, list by next renewal (shared shows share and "ليك X عند N صحاب" — reminders link to People in Phase 7), one-tap add chips (names only; price typed). Empty state from `04-SCREENS.md`.

## Task 35: Payday ritual

- Per `Payday.dc.html` and decision 5: turquoise hero, three numbered steps (jar share 5/10/15%/amount; debts step hidden until Phase 7; fixed costs with "عدّل"), the "المرتب كله راح فين" bar, the month ticket card with the mini-ticket strip and "أكتر من الشهر اللي فات بـ N", "اطبع تذاكر الشهر". Trigger from income and from the roll's salary row.
