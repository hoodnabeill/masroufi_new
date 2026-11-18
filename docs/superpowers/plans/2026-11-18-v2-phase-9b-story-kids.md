# Masroufi v2 Phase 9b (monthly story and kids' allowance) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** حكاية الشهر — five full-screen yellow slides when the pay cycle closes (a short two-slide story for thin months), shareable with or without amounts and replayable from the album; مصروف العيال — each child's weekly allowance as a small ticket, advances, chores, a matched jar, one handover button, and a child screen that cannot be left without the parent's PIN or fingerprint. The shared home is not built.

**Spec:** `12-BUILD-PROMPTS.md` Prompt 9b; `02-FEATURES.md` §30–31; `11-DATA-AND-RULES.md` ("Monthly story", "Kids' allowance", "Lock" for the PIN rules); `04-SCREENS.md` "Story", "Kids"; mocks `screens/Story|Kids.dc.html`; `05-MOTION.md` (story slide, child's handover); `06-HAPTICS.md` (story Tick / best-month Milestone; handover Heavy; advance and chore Confirm; child's jar goal Milestone; PIN digit Tick; unlock Tap); `07-COPY.md`; `09-PREMIUM.md` (one child free).

## Global constraints

As Phases 3–9a (Migrations only, never edit old ones; strings ar + en; copy verbatim; Long piastres; injected Clock; the test rules in the carry-forward "Phase 8 notes"; poll CI at most once a minute; never push while your own CI run is in progress).

## Decisions taken in this plan (owner may override)

1. **"The month closes" = the pay cycle closes.** The story is built once, at the first `printToday` of a new cycle, from stored day rows, entries, jar movements and collected cards, and saved (`month_story`: cycle start, cycle end, a JSON payload, shownAt). Replaying reads the payload only.
2. **The suggestion slide** picks one line from a small fixed set by plain rules on the month's data (e.g. the envelope that ran out earliest, the weekday that was most often over, a habit that grew); builder's wording, listed as guessed copy; nothing invented when no rule applies (then a neutral line).
3. **Sharing** renders the slide (or the summary card) to a bitmap on the phone and opens the share sheet through the existing FileProvider; "شاركها من غير أي مبالغ" is on by default and draws every amount as dots; counts, streaks and the month name stay.
4. **Kids' money:** the allowance is a weekly recurring item ("مصروف العيال", kind `KIDS`, schedule `WEEKLY` on the handover day, amount = this week's handover total, paid from the kids' envelope's category). So the handovers due before payday are reserved like any fixed cost and never punch the ticket; "handover" = paying that item once (one entry). `RecurringKind.KIDS` and `ScheduleType.WEEKLY` are new enum values (stored as text; no data change). The kids' envelope is the one named "العيال" if present, else created on the first child ("ظرف العيال", builder's wording).
5. **Child spending** ("left until handover") is recorded in a `child_spend` table (not an entry: the parent already paid the handover); spending above what is left is not recorded there — the child screen says so and the parent logs it as their own entry.
6. **The child screen** is a persisted mode (`childModeId` in settings). While it is set, the app opens straight into that child's screen (cold start, back, recents) and shows nothing else; leaving needs the parent's PIN or fingerprint. The first time, the app asks the parent to set a PIN. The PIN store is built now per `11-DATA` "Lock" (4–6 digits, salted slow hash, kept encrypted with an Android Keystore key, wrong-try waits 30 s doubling to 15 min) and Phase 11's app lock reuses it. Android screen pinning (`startLockTask`) is not used (it shows a system prompt each time); the persisted mode covers leaving and coming back.
7. **Free:** one child; more children show the Plus card; extra children are kept read-only if Plus ends.

---

## Task 49: Story and kids data

- Migration 7→8: `month_story` (decision 1); `child` (id, name, birthYear, color, weekly, jarLabel?, jarTarget?, matchPer?, matchAdd?, archived), `chore` (id, label, bonus, childId? = any), `chore_tick` (id, choreId, childId, weekStart), `child_advance` (id, childId, amount, requestedAt, answer WAITING/APPROVED/DECLINED, weekStart), `child_jar_move` (id, childId, amount, at, kind SAVE/MATCH/WITHDRAW), `child_spend` (id, childId, amount, at, note?). Migration test.
- `StoryRepository`: build at the cycle change (idempotent), payload per `11-DATA` "Monthly story" (torn under / over / never; vs the previous cycle and the best cycle, "best month" only when true; top three envelopes by spend; the largest single entry; total moved to the jar; cards won; longest streak; the opening's best day; the suggestion per decision 2; thin = fewer than 7 torn days → two slides, no comparisons), unseen story flow for Home, list for the album, mark shown.
- `KidsRepository`: children CRUD (Free limit 1 via Entitlements, read-only extras when not Plus), the KIDS recurring item kept in sync with the week's total (decision 4), handover amount per child = weekly + ticked chore bonuses + match earned this week − approved advances, the total, handover (one payment of the item, resets the week), advances (≤ half of next week's allowance, one open per child, approve/decline), chores CRUD and ticks, the child's jar (save, withdraw, match counted in whole steps of X on money saved this week, goal reached flag), child spending and "left until handover" (never below 0), weeks by the handover day.
- `PinStore` (decision 6) + `ChildMode` (set / clear after verify).
- Tests per rule (thin month, best month only when true, comparisons, top three, idempotent build; handover sum incl. the mock's 70 + 150 = 220, chores, match steps, advance limits, left never below 0, Free limit, PIN hash verify and the wait schedule, child mode persisted).

## Task 50: Monthly story screens

- Per `Story.dc.html`: full-screen yellow, segmented progress (5 s per slide, pauses while held), tap left half = next, right half = back (RTL as drawn), hold to pause; slides per `04-SCREENS` (1 opening: month name and best day; 2 the day grid "قطعت بإيدك N تذكرة من M" with legend and the comparison line; 3 where the money went; 4 the jar, cards, longest streak; 5 the suggestion and the shareable summary card); the short two-slide version; the "شاركها من غير أي مبالغ" switch (on by default) and "شارك الشريحة دي" (decision 3). Motion per `05-MOTION` (cross-fade 200 ms, the big number counts up once in 600 ms); haptics Tick per slide, Milestone once when the best-month line shows.
- Shown on the first open after the cycle closes (once), and replayable from a "الحكايات" row in the album (list of months).

## Task 51: Kids' allowance and the child screen

- Per `Kids.dc.html`, reached from the People tab (a card/link): monthly total and the envelope line, "المصروف بيتسلّم كل {day}", one coloured ticket per child (initial, name and age, weekly, left until handover, punches row, then either the jar — goal, bar %, the match rule line — or a pending advance with "ماشي، تتخصم" / "يستنى السبت"), the chores list with "ضيف" and ticks (Confirm), the child-screen hint card with Qersh ("ورّيهم تذكرتهم"), and "سلّم مصروف {day} · {total}" (Heavy; numbers roll up and punches reset per `05-MOTION`). Add/edit child (pushed form: name, birth year, colour, weekly, jar goal, match rule). Empty state from `04-SCREENS`. Free limit → Plus card.
- Child screen (decision 6): the child's ticket and jar only, logging a spend ("صرفت") and asking for an advance; the PIN set-up the first time; leaving through PIN pad (Tick per digit, Tap on success) or the system biometric prompt (`androidx.biometric`, already a dependency); wrong-try waits shown plainly. Nothing of the parent's money is visible.
