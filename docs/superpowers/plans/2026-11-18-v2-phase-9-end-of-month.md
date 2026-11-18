# Masroufi v2 Phase 9 (end-of-month mode) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** End-of-month mode (وضع آخر الشهر), free forever: offered when money per day runs low or switched on by hand; small equal tickets until payday, bills reserved, the two biggest optional habits pausable (reminder only), the no-spend-day challenge with the tight-fist stamp, and where emergency money could come from. The paywall never shows while it is on.

**Spec:** `12-BUILD-PROMPTS.md` Prompt 9; `02-FEATURES.md` §6; `11-DATA-AND-RULES.md` ("The daily ticket", "End-of-month mode", "Streak, levels, stamps"); `04-SCREENS.md` "LastDays" and "Main" 7; mock `screens/LastDays.dc.html`; `03-DESIGN-SYSTEM.md` (carnelian), `05-MOTION.md`, `06-HAPTICS.md`, `07-COPY.md`, `09-PREMIUM.md` (never shown in end-of-month mode).

## Global constraints

As Phases 3–8 (Migrations only, never edit old ones; strings ar + en; copy verbatim; Long piastres; injected Clock; tests wait for ViewModel state to settle and compare Long with Long; poll CI at most once a minute).

## Decisions taken in this plan (owner may override)

1. **Mode state** lives in `tazkara_settings`: on/off, until (the next payday), paused category ids, and the dates of no-spend days closed in the mode. It turns off by itself when the pay cycle changes. The existing end-of-month row (Spendable − Reserved ≤ 0) stays as it is and always counts as "in the mode".
2. **Small tickets:** while the mode is on, each day's ticket = floor((Spendable − Reserved) ÷ days left including today), all equal, no separate carry; the strip shows the remaining days with that amount and marks the days a reserved bill is due ("يوم 20 فيه فاتورة النت (250)، ومحجوزة من دلوقتي برّه الـ 540.").
3. **Leftovers in the mode** stay in the pool (default destination "spread over the remaining days"; the jar is still offered). That is what makes the challenge rule true: an unspent day's ticket ÷ remaining days is added to every remaining ticket.
4. **No-spend challenge:** a day closed by hand with no SPENT entries while the mode is on counts; "0 / 2"; the second earns the tight-fist stamp (Task 40's `StampAwarder`, idempotent). The mock's "+7 جنيه لكل يوم" is computed (today's ticket ÷ remaining days), never fixed.
5. **Offer:** Home shows one offer card when today's ticket < 60% of the average printed ticket of this cycle (at least 3 printed days), or when today is an end-of-month row; dismissing it hides it for the cycle. The pay-cycle card's link always opens the screen. The offer uses the first-week card slot rules (never two cards at once).
6. **Paused habits:** the two non-essential categories (`SeedData` essential flag) with the highest spend this cycle. Pausing only adds a gentle line after saving an entry in that category ("لو سجّلت حاجة موقّفة هفكّرك بس، من غير زعل." on the screen; the after-save line is builder's wording). Never blocks.
7. **"لو اتزنقت":** the free jar total (→ the jar's "فكّ منها"), the person who owes the user the most (→ the gentle reminder), the emergency pot. Rows hide when 0.
8. **Plus never shows** while the mode is on: expose `EndOfMonthMode.isOn` for Phase 10's paywall rules; the "بلس قريب" placeholder sheet already in the app is not opened from Plus-locked cards while it is on (they show their card without the button).

---

## Task 45: End-of-month data

- `EndOfMonthRepository`: state (decision 1), switch on (until next payday) / off, auto-off on cycle change, the pool and days left, small ticket (decision 2), the day strip with bill days, biggest optional habits with this cycle's spend, pause/unpause, the no-spend counter and tight-fist award (decision 4), the per-day boost of one more no-spend day, rescue sources (decision 7), the offer rule (decision 5) with dismiss-for-cycle.
- `TicketRepository.printToday`: when the mode is on, print the small ticket (decision 2). Close day in the mode: default destination per decision 3.
- `EntryRepository` / add path: after saving a SPENT entry in a paused category, emit the gentle line through `FeedbackBus`.
- `EndOfMonthMode.isOn` flow for Plus rules (decision 8).
- Tests per rule (60% trigger with and without 3 days, EOM row counts, small ticket math incl. the mock: 540 over 9 days = 60, bill outside the pool, leftover stays in pool and lifts tomorrow, no-spend counter and stamp once, habits exclude essentials, auto-off at payday, offer dismissed for the cycle).

## Task 46: The end-of-month screen and wiring

- Screen per `LastDays.dc.html` (pushed): carnelian hero ("فاضل N أيام على المرتب ومعاك", the pool, "يعني X جنيه في اليوم. تتعمل، وأنا معاك."), "N تذاكر صغيرة · كل واحدة بـ X" strip with bill days and the bill line, "أكتر حاجتين بيسحبوا منك" with the pause toggles ("وقّفها لحد المرتب") and the reminder line, the challenge card ("تحدي: يوم من غير صرف", "x / 2", the computed line, the tight-fist mention), "لو اتزنقت" rows, the button "شغّل الوضع لحد يوم {payday}" (switches on; when on, the button becomes the off action — builder's wording). Shortfall state (pool ≤ 0): the hero states what is short and the rescue rows come first.
- Home: the offer card (decision 5), the pay-cycle card link, the ticket in the mode's look per `03-DESIGN-SYSTEM.md` if it defines one; no first-week cards while on (already the rule).
- Plus-locked cards hide their button while the mode is on (decision 8).
- Haptics/motion per the docs (toggle `snappy`; stamp earned = Milestone).
- Tests: ViewModel tests for hero numbers, strip and bill line, toggles, challenge line, rescue rows hidden at 0, button on/off, offer card shown/dismissed, Plus button hidden in the mode.
