# Masroufi v2 Phase 4 (onboarding and empty states) Implementation Plan

> **For agentic workers:** execute task by task with one implementer subagent per task; a task is done when CI is green on its head commit (owner instruction 2026-11-17: no reviewer loop unless asked).

**Goal:** A first-time user goes from opening the app to their own ticket in under a minute — welcome, payday, money, fixed costs, the reveal — and lands on Home with the add sheet open. Empty states and the first-week cards teach the app without a tour.

**Spec:** `masroufi-tazkara-handoff/08-ONBOARDING.md` (flow, principles, edge cases, first-week cards), mocks `screens/Onboarding|ObPayday|ObMoney|ObFixed|ObTicket|EmptyStates.dc.html`, `07-COPY.md` (empty states), `05-MOTION.md` §4 (slow print, Qersh wave), `13-ICON.md` (illustrations). Foundation + Phase 3 decisions: `docs/superpowers/plans/2026-11-13-v2-foundation-carry-forward.md`.

## Global constraints

Same as Phase 3 (Long piastres, Arabic default + `values-en`, RTL, dark only, `Tz`/`TzType` only, injected Clock, copy verbatim, every string in strings.xml, CI-only verification, LF files). No account, no email, no permission up front: notifications permission only when the user picks a reminder time.

## Decisions taken in this plan (owner may override)

1. Skipping the money question ("مش عايز أقول دلوقتي") sets `moneyUnknown = true`; while true, `printToday` prints a fixed starter ticket of 100 EGP (design call 8) and Home shows a first-week card asking for the amount after the third entry. Entering an amount clears the flag.
2. Each fixed cost chosen in onboarding becomes a recurring item due the day before the next payday (`08-ONBOARDING`: exact dates later). Kinds: الإيجار/النت/الكهربا والغاز/دروس العيال/اشتراك الجيم/حاجة تانية → BILL, الجمعية → GAMEYA, قسط → INSTALLMENT.
3. The reminder time and the permission are taken in onboarding; the daily reminder itself is scheduled in Phase 11 (Settings/notifications). The choice is stored now.
4. "عندي نسخة احتياطية" (restore) is hidden until Phase 11 builds backup.
5. Payday today/tomorrow: the question "قبضك قرّب. نحسب التذكرة من بعد القبض؟" gets the answers "أيوه، من بعد القبض" / "لأ، من النهارده" (builder's wording). "Yes" prints the reveal using the next cycle; "No" prints today normally.
6. The Day 7 first-week card (Plus trial) waits for Phase 10.

---

## Task 22: Onboarding data and rules

**Files:** `data/prefs/SettingsStore.kt` (+ `onboardingStep: Int`, `moneyUnknown: Boolean`, `reminderHour: Int?`, `firstWeekCardsDismissed: Set<String>`, `firstOpenDay: Long?`), `data/repo/OnboardingRepository.kt`, `data/repo/TicketRepository.kt` (starter ticket), tests.

- `OnboardingRepository`: `setPayday(Payday)`, `setMoney(piastres: Long?)` (null = skipped → `moneyUnknown`; else sets الكاش's opening balance so its balance equals the amount, clears the flag), `setFixedCosts(List<FixedCost>)` (replaces onboarding-created recurring items; `FixedCost(kind label, amount)`), `previewTicket(): TicketCalc` (what printToday would print now, without writing), `finish(reminderHour: Int?)` (stores the hour, `onboarded = true`, deletes today's OPEN row if it has no entries, then `printToday()`), `step` persistence (`setStep(n)`).
- Starter ticket: when `moneyUnknown`, printToday stores amount = base = 10000, no carry, not end-of-month.
- Edge: money < fixed costs → `previewTicket()` returns EndOfMonth; expose the shortfall.
- Tests: `previewMatchesMock` (5870 money, payday 25, fixed 2750, today 11-12 → 24000), `skippedMoneyPrintsStarter`, `fixedCostsBecomeBillsBeforePayday`, `moneyLessThanFixedIsEndOfMonth` (shortfall 400), `stepPersists`, `finishPrintsFreshTicket`.

## Task 23: Onboarding screens and the reveal

**Files:** `feature/onboarding/*` (`OnboardingViewModel`, `WelcomeStep`, `PaydayStep`, `MoneyStep`, `FixedStep`, `RevealStep`), `ui/AppRoot.kt` (start destination = Onboarding while `!onboarded`; resume at the saved step), strings, tests.

- Exactly the five mocks: progress = three bars (welcome and reveal not counted); Qersh bubbles; welcome waving (arm rotates 20° and back once), language switch "English"/"عربي" in the corner (`AppCompatDelegate.setApplicationLocales`, stays on the same step), privacy line, "يلا بينا".
- Payday: chips 1, 5, 10, 15, 25, 28, آخر الشهر, يوم تاني (opens a 1–31 picker), dashed row "مش ثابت · يومية، شغل حر، أو مصروف من البيت"; the turquoise echo "يعني فاضل 13 يوم على القبض الجاي." updates instantly; default 25 preselected; "تمام، اللي بعده".
- Money: amount with the add-sheet keypad and +100/+500/+1,000 chips, caption "الكاش والمحفظة والبنك كلهم مع بعض", "تمام، آخر سؤال", text link "مش عايز أقول دلوقتي".
- Fixed: chips per the mock; tapping a chip opens an inline amount field with the keypad; chip then shows its amount; tap again removes; "محجوز لحد دلوقتي {total}"; primary "اطبعلي التذكرة", link "مفيش حاجة، كمّل".
- Reveal: the ticket prints with §4's slow version (700 ms, three steps, Tick each) — label "تذكرة يوم · من النهارده لحد {payday}", the number, "تقدر تصرفهم كل يوم وإنت مطمّن.", stub facts "معاك {money} · محجوز {fixed} · {days} يوم", the loop paragraph, the reminder card (8 م / 9 م selected / 10 م / من غير; picking a time requests POST_NOTIFICATIONS on API 33+), "تنبيه واحد في اليوم. مش هزعجك بغيره.", primary "اخرم أول خُرم" → `finish()`, navigate to Home and open the add sheet. Edge cases from `08-ONBOARDING.md` (shortfall copy with Qersh thinking and end-of-month; payday today/tomorrow question; leaving mid-flow resumes).
- Tests: `flowSavesEachAnswer`, `resumeAtSavedStep`, `paydayEchoDays`, `fixedChipToggle`, `revealOpensAddSheet`, `shortfallShowsCopy`.

## Task 24: Empty states and first-week cards

**Files:** `ui/paper/Illustrations.kt` (blank sheet + Qersh surprised, empty jar + Qersh, Qersh asleep with "z"), `ui/components/EmptyState.kt` (Qersh/illustration, title, line, optional action), `feature/home/FirstWeekCards.kt`, Home wiring, strings, tests.

- `EmptyState(illustration, titleRes, lineRes, actionRes?, onAction?)` per `EmptyStates.dc.html`; all twelve empty-state texts from `07-COPY.md` added to strings now (screens use them as they are built). An "all caught up" row ("خلّصت كل حاجة / مفيش فواتير جاية الأسبوع ده. استمتع.").
- Skeleton component reused from Home exposed as `RowSkeleton` for later lists.
- First-week cards on Home (`08-ONBOARDING.md` table), one at a time, dismissible forever, never during end-of-month: after first entry, first evening (after 18:00 on day 1), day 2, day 3, day 5; plus the money card when `moneyUnknown` and ≥ 3 entries ("معاك كام دلوقتي؟" opens a small sheet with the keypad → `setMoney`). Days counted from `firstOpenDay`.
- Tests: `cardOrderAndOneAtATime`, `dismissedNeverReturns`, `noCardsDuringEndOfMonth`, `moneyCardAfterThreeEntries`.
