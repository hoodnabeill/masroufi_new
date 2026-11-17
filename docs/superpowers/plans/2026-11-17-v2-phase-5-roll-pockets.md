# Masroufi v2 Phase 5 (the roll and pockets) Implementation Plan

> **For agentic workers:** one implementer subagent per task; a task is done when CI is green on its head commit (owner instruction 2026-11-17: no reviewer loop unless asked).

**Goal:** The roll (الدفتر) shows the money history as day stubs with search, month switch and filters; any entry opens in the add sheet to edit, and delete has a 5-second undo. The pockets screen (الجيوب) and one pocket's screen (جيب واحد) show balances, the cash counter, quick moves, the monthly limit, fees, and pasting a bank/wallet SMS into a ready entry.

**Spec:** `04-SCREENS.md` ("Roll", "Wallets", "WalletDetail", "Screens not drawn": entry detail/edit, search results), `02-FEATURES.md` §7, §8, §24, `11-DATA-AND-RULES.md` ("SMS", "One pocket", "Safety rules"), `10-PERFORMANCE.md` (undo, virtualised lists), mocks `screens/Roll|Wallets|WalletDetail.dc.html`, `07-COPY.md`.

## Global constraints

As Phases 3–4. Plus: **schema changes from now on go through a real `Migration`** (owner may have installed a Phase 3/4 build): bump `TazkaraDatabase` to version 2 with a hand-written `MIGRATION_1_2` (`ALTER TABLE … ADD COLUMN`), add a Robolectric migration test that opens a v1 database created with the v1 SQL and checks data survives. No destructive fallback.

## Decisions taken in this plan (owner may override)

1. The roll browses **calendar months** (the mock's "نوفمبر" switcher); ticket logic stays on the pay cycle.
2. "سوّي الرصيد" (reconcile) records a new entry kind `ADJUST` with a signed amount (the difference); it changes the pocket balance, never punches the ticket, never counts as income for a not-fixed pay cycle, and shows in the roll as "تسوية رصيد" (builder's wording).
3. Quick actions open the add sheet prefilled as a transfer: "حوّل بين الجيوب" (MOVED, pick both), "سحبت من الماكينة" (MOVED bank → cash, fee field shown), "شحنت المحفظة" (MOVED bank → mobile wallet).
4. A pocket's "matches the last message" uses the balance parsed from the last pasted SMS for that pocket (stored on the pocket with its time); the message text itself is never stored.
5. A "جيب جديد" dashed row on the pockets screen opens a pushed "new pocket" screen (name, kind, opening balance, counts in ticket) — wording is the builder's; the screen pattern follows `04-SCREENS.md` "Screens not drawn".
6. The low-balance alert is a stored switch + threshold now; the notification itself comes with Phase 11.
7. Fee tips: the mock's tip ("جرّب تجمّع السحب في مرة واحدة أول الأسبوع…") shows when cash-out fees are the larger share; otherwise no tip (no invented advice).

---

## Task 25: Data for the roll and pockets

- Migration 1→2: `pocket` + `monthlyLimit INTEGER`, `lowBalanceThreshold INTEGER`, `lowBalanceAlert INTEGER NOT NULL DEFAULT 0`, `lastSmsBalance INTEGER`, `lastSmsAt INTEGER`; `EntryKind.ADJUST` (signed `amount`; balance SQL adds it; `punches` false; excluded from `latestReceivedAt`; `NewEntry` allows a negative amount only for ADJUST). Test `migration1to2KeepsData`.
- `RollRepository`: `month(yearMonth): Flow<RollMonth>` with summary (spent = punched SPENT + fees in the month; received = RECEIVED; tickets "closed under / closed" for rows in the month), day groups newest first (date, ticket row or null, entries, kind of day: ticket day / non-ticket day), filters (ALL, SPENT, INCOME, RECURRING), `search(query): Flow<List<EntryRow>>` matching note and category name (normalised Arabic like the quick-log parser), `months(): List<YearMonth>` that have entries.
- `PocketRepository`: `observePockets(): Flow<List<PocketView>>` (balance, kind, counts, last activity), `addPocket(...)`, `update(...)`, `reconcile(pocketId, counted: Long)` (ADJUST entry for the difference; none when equal), `cashCount(200s, 100s, 50s, change)` helper, `movedThisMonth(pocketId)`, `feesThisMonth(pocketId)` grouped (cash-out = MOVED into a CASH pocket, transfer = other MOVED), `recordSms(pocketId, parsedBalance)`.
- `EntryRepository.delete` is already real; the 5-second undo lives in the ViewModels (hide now, delete after 5 s, cancel on undo).
- Tests for each rule above.

## Task 26: SMS paste parser

- `domain/SmsParser.kt`: `parseSms(text): ParsedSms?` → amount, direction (SPENT / RECEIVED / MOVED_OUT), counterparty or merchant text if present, remaining balance if present. Arabic and English bank/wallet phrasings: debit (خصم، سحب، دفع، شراء، تحويل منك / debited, withdrawn, purchase, sent), credit (إضافة، استلام، تحويل إليك، إيداع / credited, received, deposit), balance (رصيدك، الرصيد المتاح / balance, available). Digits Western/Arabic-Indic, "," thousands, decimals. Text from ordinary mobile numbers cannot be detected from a paste — so every paste is parsed and only saved after the user confirms (`11-DATA` "SMS").
- Tests with at least 12 made-up messages (no real brand claims), including the mock's "تم خصم 40 جنيه من محفظتك. رصيدك الحالي 1,150 جنيه.", messages without balance, English, Arabic-Indic digits, and nonsense → null.

## Task 27: The roll (الدفتر)

- Roll tab replaces the placeholder, exactly per `Roll.dc.html`: title, search and month buttons, three summary tiles, filter chips (الكل / صرف / دخل / متكرر), the roll: today's stub expanded with its entries (category · note, pocket · time, "+N فكة" when rounded up, amount), earlier ticket days collapsed with "صرفت X في N مرات" and the under/over line ("تحت التذكرة بـ 5 · راحوا الحصّالة", "فوق التذكرة بـ 35 · اتوزّعوا على باقي الأيام", destination wording per row), non-ticket days (recurring "متكرر · من ظرف …، مش من التذكرة" — envelopes come in Phase 6, so say "متكرر · مش من التذكرة" until then; salary "المرتب نزل") in neutral/turquoise. Expanding a day: height springs open, entries fade in 40 ms after.
- Tap an entry → add sheet prefilled (type, amount, category, pocket, note, fee) with a delete link "امسحها"; save edits via `EntryRepository.edit`; delete → row disappears, 5-second undo bar "اتمسحت. رجّعها؟" (Heavy haptic on the real delete).
- Search: results as roll rows with the matched word in yellow; no match → empty state "ملقتش حاجة بالاسم ده".
- Empty roll → `EmptyState` "الدفتر لسه أبيض" with "سجّل أول حاجة" opening the add sheet. Loading → `RowSkeleton`. LazyColumn with stable keys; months load as the user scrolls.

## Task 28: Pockets (الجيوب) and one pocket (جيب واحد)

- Pockets screen per `Wallets.dc.html`, reached from the ticket tap (Home's ticket opens the pockets screen — `02-FEATURES` §1) and from the new-pocket card on Home day 2: total + share bar, the cash card with the note counter (200/100/50/فكة steppers) and "عدّ اللي في جيبك وسوّي الرصيد", other pockets, three quick actions, the SMS card ("الرسايل بتتسجّل لوحدها" title is the Plus auto-read; Free shows a paste button — wording "الصق رسالة البنك" is the builder's) with a parsed preview ("قرش فهمها: …") and "الرصيد مطابق" when it matches, the privacy line, the "جيب جديد" row.
- One pocket per `WalletDetail.dc.html`: hero in the pocket's colour, "مطابق لآخر رسالة · …", four actions (شحنت / حوّلت / سحبت كاش / سوّي الرصيد), monthly limit card (typed limit, moved this month bar, the warning line, "الحد ده إنت اللي كتبته زي ما الشركة قالتلك."), fees card with the tip rule, recent entries + "الدفتر كله", switches (counts in ticket; low-balance alert with threshold). Cash pockets show the note counter instead of limit and fees.
- New pocket screen (decision 5). Archiving a pocket asks first (the only confirm besides restore, `10-PERFORMANCE`).
- Tests: VM tests for reconcile, cash counter math, limit warning, fee grouping, SMS paste → prefilled sheet.
