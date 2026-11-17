# Masroufi v2 Phase 7 (people) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** The People tab (الناس): loans both ways with gentle reminders, the tab at a kiosk (النوتة), quick split, the duty ledger (النقوط, Plus), and the gameya (الجمعية, one free). Records only — money moves between people as usual.

**Spec:** `02-FEATURES.md` §14–17; `11-DATA-AND-RULES.md` "People"; `04-SCREENS.md` "People", "Gameya", "Screens not drawn" (person detail, duty ledger list, new person/gameya); mocks `screens/People|Gameya.dc.html`; `07-COPY.md` (buttons فكّره بلُطف / سدّد النوتة, feedback "النوتة اتسدّدت…", "بعتله رسالة بلُطف.", empty state "محدش ليه عندك حاجة").

## Global constraints

As Phases 3–6 (Migrations only, strings ar + en, copy verbatim, Long piastres, injected Clock, Plus via `Entitlements` + `PlusLocked`).

## Decisions taken in this plan (owner may override)

1. **Money for loans:** a new `EntryKind.DEBT` with a signed amount moves the pocket (lend or repay = −, borrow or get repaid = +), never punches the ticket and never counts as income. Each DEBT entry links to its `debt` row. Logging a loan without touching a pocket is allowed (pocketId null → no entry, just the debt row).
2. **Quick split:** the user paid the whole bill from a pocket → one SPENT entry for their own share (punches today) + one DEBT entry (−) for the others' shares, and one debt "ليك" per chosen person.
3. **The tab:** items are recorded without touching a pocket; settling logs one SPENT entry for the total from the chosen pocket (punches today, per `11-DATA`) and marks the items settled.
4. **Shared subscriptions:** each logged payment of a subscription shared with N people creates one debt "ليك" for the others' total, named after the subscription ("{name} · {N−1} صحاب").
5. **Payday ritual step 2** lists debts the user owes; ticked ones are paid at apply time (DEBT entries).
6. **Reminders** open the system share sheet with a polite prefilled message (builder's wording, plain, no amounts beyond the debt); the app sends nothing itself.
7. **Gameya** stores members in turn order, the monthly amount, the start month, the user's turn, per-member payments per month (records only), and a payout plan (rows: label + amount, optionally linked to a season or goal). The user's own monthly installment is the gameya's recurring item (kind GAMEYA), paid like any fixed cost. Free: one active gameya.

---

## Task 36: People data

- Migration 3→4: `person` (id, name, phone?, kind PERSON/SHOP, updatedAt), `debt` (id, personId, direction THEY_OWE/I_OWE, amount, reason?, date, dueDate?, settled, sourceRecurringId?), `tab_item` (id, personId (shop), label, amount, date, settled, settledEntryId?), `occasion` (id, name, date, whose MINE/THEIRS, personId?), `gift` (id, occasionId, personId, amount, direction RECEIVED/GIVEN), `gameya` (id, name, monthly, startMonth (yyyymm), myTurn, recurringId?, archived), `gameya_member` (id, gameyaId, name, turn, phone?), `gameya_payment` (id, gameyaId, memberId?, month (yyyymm), paid), `gameya_plan` (id, gameyaId, label, amount, seasonId?, goalId?); `entry.debtId INTEGER`. `EntryKind.DEBT` (signed; balance SQL adds it; never punches; not income). Migration test.
- `PeopleRepository`: totals (ليك / عليك, with how much of عليك is on the tab), per-person balance (they owe − I owe, plus tab items), list with filters (الكل / سلف / النوتة / الواجب), lend/borrow/repay (decision 1), split (decision 2), tab add/settle (decision 3), person CRUD, debts due/overdue.
- Subscription share debts (decision 4) hooked into the payment path; payday ritual debts (decision 5) in `PaydayRitualRepository`.
- `DutyRepository`: occasions, gifts, suggestion "X أو أكتر" = what that family gave the user at the user's occasion, upcoming occasion reminder data.
- `GameyaRepository`: payout = monthly × members, my turn month, months until, paid so far (my payments), this month's paid/unpaid members, swap turns, plan rows, statement text for sharing, Free limit 1.
- Tests per rule.

## Task 37: People tab, person detail, new debt / tab / split

- People tab per `People.dc.html`: two totals, filter chips, the list (avatar initials, name, context line, "ليك 500"/"عليك 120", one action: "فكّره بلُطف" (they owe) or "سدّد النوتة" (shop) or "سدّد" (I owe — builder's)), the lilac duty-ledger card (Plus-locked when not Plus), the gameya card, the quick-split hint card → split sheet (amount keypad, pocket, people multi-select incl. "أنا", equal shares with editable amounts). Empty → "محدش ليه عندك حاجة". A "+" in the header (builder's) → new debt/person sheet (they owe me / I owe / tab item).
- Person detail (pushed): totals, history rows (debts, payments, tab items), "سدّد" / "فكّره بلُطف", edit person, settle all tab items.
- Haptics: settle Heavy; reminder sent feedback "بعتله رسالة بلُطف.".

## Task 38: Duty ledger (Plus)

- Duty ledger list screen (pushed from the card): occasions grouped (mine / theirs), each with gift rows (name + amount, received/given); add occasion, add gift; upcoming occasion card with the suggestion "الواجب يتردّ بـ X أو أكتر" and "هما نقّطوك في فرحك X"; "في فرحك جالك {total} من {n} واحد" summary for the user's own occasion. Seasons' personal-occasion line (Phase 6 omitted) now shows "أبوه نقّطك X في فرحك."-style text from the ledger when linked.

## Task 39: Gameya

- Gameya screen per `Gameya.dc.html`: the wheel (members in a circle: paid out, this month's turn, the user's turn), the user's turn card ("دورك {n} · {month}", "بعد {k} شهور", the forced-savings line), this month's installment ("قسط {month} · {amount}", "إنت دفعت" state, "{x} من {n} دفعوا. لسه:" + names + "فكّرهم"), payout plan bar and rows, "بدّل دورك مع حد", "ابعت الكشف للجروب" (share sheet with the statement text), the records-only line. New gameya (pushed form: name, monthly, members in order, start month, my turn; creates the GAMEYA recurring item). Free limit 1 → Plus card. Empty → "مش داخل جمعية دلوقتي".
