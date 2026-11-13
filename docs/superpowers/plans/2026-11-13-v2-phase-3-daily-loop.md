# Masroufi v2 Phase 3 (the daily loop) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The owner opens the app, logs three expenses (two taps each once a spend repeats), watches three holes punch, tears the ticket at night and gets a proverb card — exactly as drawn in `Main`, `AddPunch`, `CloseDay` and `Proverb`, with the four authored moments (punch, tear, card flip, new ticket).

**Architecture:** Phase 0–2 foundation on branch `v2` (design system in `ui/`, rules in `domain/`, Room + repositories in `data/`). Phase 3 adds a navigation shell, three screens and the add sheet, each with a Hilt `@HiltViewModel` exposing one `StateFlow<…UiState>`; ViewModels update state optimistically, then call the repositories.

**Tech Stack:** As the foundation (Kotlin 2.2, Compose BOM 2025.10.01, Room 2.7, Hilt, DataStore, Navigation Compose 2.8 type-safe routes). No new dependencies: speech uses the system `RecognizerIntent`; sharing uses `FileProvider` from androidx.core.

**Spec:** `docs/superpowers/specs/2026-11-13-masroufi-v2-tazkara-design.md` → `masroufi-tazkara-handoff/` (`04-SCREENS.md` "The daily loop", `05-MOTION.md`, `06-HAPTICS.md`, `07-COPY.md`, `10-PERFORMANCE.md`, `11-DATA-AND-RULES.md`, `14-PROVERBS.md`, mocks `screens/Main|AddPunch|CloseDay|Proverb|EmptyStates.dc.html`). Carry-forward and rulings so far: `docs/superpowers/plans/2026-11-13-v2-foundation-carry-forward.md`.

## Global Constraints

- Everything in the foundation plan's Global Constraints still binds (package, no INTERNET, Long piastres, Arabic default + `values-en`, RTL, dark only, injected `Clock`, CI-only verification, LF files).
- Copy is word for word from `07-COPY.md` / the mocks. Every user-facing string lives in `res/values/strings.xml` (Arabic) with an English translation in `res/values-en/strings.xml` (builder's translation, owner reviews later). Numbers are formatted with Western digits and "," thousands separators.
- 1 px in a mock = 1 dp; the mock wins over prose.
- Owner decision 2026-11-13: **a transfer/cash-out fee punches today's ticket** (adds to today's spent and makes one hole).
- No Plus screens; Plus-only lines (round-up) appear only when `Entitlements.isPlus` is true.
- Every screen has loading (skeleton, `10-PERFORMANCE.md`), content, empty and error states; no spinners; content cross-fades in 160 ms; skip the skeleton if ready in < 150 ms.
- Saving updates the screen before storage finishes (optimistic), rolls back with "ماتسجّلتش. جرّب تاني." on failure; double-tap guard 400 ms; never disable a button while saving.
- Reduced motion: moments become a still state with the same haptic (`05-MOTION.md` rule 7, foundation ruling 4).

## Review Focus

1. **App opened after midnight while Home is showing yesterday** → Home re-prints on resume and at midnight; the old ticket is never punched by a new day's entry. Pinned in Task 17 (`midnightReprintsTicket`).
2. **Double tap on "اخرم التذكرة" or a repeat chip** → exactly one entry. Pinned in Task 18 (`doubleTapLogsOnce`).
3. **Tearing the ticket twice (back gesture, re-open close screen)** → one jar movement, one card. Pinned in Task 19 (`tearTwiceOneCard`).
4. **Amount typed as "12.5" or "0" or 9 digits** → piastres exact, zero rejected with the reject shake, length capped (8 digits before the point, 2 after). Pinned in Task 18 (`keypadEdges`).
5. **Proverb deck exhausted / rare cards** → ordinary deck never hands out a rare number; when all ordinary cards are collected the reward shows a random favourite and "معاك الألبوم كله". Pinned in Task 15 (`ordinaryDeckSkipsRare`, `allCollected`).

---

## Task 14: Data for the daily loop

**Files:** modify `domain/TicketRules.kt`, `data/db/Entities.kt`, `data/db/Daos.kt`, `data/db/TazkaraDatabase.kt`, `data/repo/TicketRepository.kt`, `data/repo/EntryRepository.kt`; create `data/repo/HomeRepository.kt`, `data/repo/SeedData.kt`; tests in `app/src/test/.../data/repo/HomeRepositoryTest.kt`, update `TicketRulesTest`, `TicketRepositoryTest`.

**Interfaces – Produces:**
- Fee punches (owner decision): `EntryForDay` gains `fee: Long`. `spentToday` adds `fee` for `MOVED` entries whose source pocket counts (counting, not archived, EGP) and `fee > 0`; a punch is a counted SPENT (as now) **or** a counted MOVED with `fee > 0`. `entryCount` counts both. Update `forDayWithPocket` to select `e.fee`. Tests: `transferFeePunches` (MOVED 50000 fee 500 → spent 500, holes 1), `transferWithoutFeeDoesNotPunch`.
- End-of-month row: `DayTicketEntity` gains `eomPool: Long? = null`, `eomDaysLeft: Int? = null`, written by `printToday` for `EndOfMonth`. Schema stays version 1 (pre-release, foundation ruling); add the columns.
- `DayTicketDao.forDateFlow(date): Flow<DayTicketEntity?>`.
- `EntryDao.repeatCandidates(sinceMillis): List<RepeatRow>` — `data class RepeatRow(val categoryId: Long?, val amount: Long, val pocketId: Long, val count: Int, val lastAt: Long)`: SPENT entries since the given time grouped by (categoryId, amount, pocketId) with `COUNT(*) >= 2`, ordered by count desc then lastAt desc, limit 3.
- `HomeRepository.observeHome(): Flow<HomeData>` combining today's row (`forDateFlow`), the week's rows (`range(weekStart(today), weekStart(today)+6)`), jar total, collected proverb count, streak (`streak(rows, today)` over the last 400 days), pocket balances total (`countingBalanceTotal` as a flow — add `PocketDao.countingBalanceTotalFlow()`), the cycle (`payCycleFor`) and repeat chips (last 30 days). `data class HomeData(today: DayTicketEntity?, week: List<DayTicketEntity>, jarTotal: Long, streak: Int, collected: Int, spendable: Long, cycle: PayCycle, repeats: List<RepeatChip>)`, `data class RepeatChip(categoryId: Long?, categoryName: String, amount: Long, pocketId: Long)`.
- Projection for the cycle card: `fun projectedAtPayday(spendable: Long, reserved: Long, ticket: Long, daysLeft: Int): Long = spendable - reserved - ticket * daysLeft` in `domain/TicketRules.kt` (may be negative; the card shows it only when ≥ 0). Test `projection`.
- `SeedData.ensureSeeded()` (idempotent, in a transaction, run once from `MasroufiApplication` on a background scope): if there is no pocket, insert pocket "الكاش" (`PocketKind.CASH`, opening 0, counts in ticket) and the default categories. Categories, in order, with `essential` flags from `11-DATA-AND-RULES.md` "End-of-month mode": قهوة, مواصلات (essential), أكل, بقالة (essential), فواتير (essential), إيجار (essential), صحة (essential), تعليم (essential), حاجة تانية. Icon names map to `TzIcons` (coffee, transport, food, groceries, …; `grid` for حاجة تانية). Test `seedIsIdempotent`.
- `PocketDao.countingBalanceTotalFlow(): Flow<Long>` mirroring the suspend query.

- [ ] Steps: write the tests above (plus updating the existing tests whose `EntryForDay` constructor changes), implement, push, CI green, commit `feat(v3): data for the daily loop`.

## Task 15: Quick-log parser and proverb catalog

**Files:** create `domain/QuickLogParser.kt`, `domain/ProverbCatalog.kt`; modify `domain/ProverbDeck.kt`; tests `QuickLogParserTest.kt`, `ProverbCatalogTest.kt`, update `ProverbDeckTest`.

**Interfaces – Produces:**
- `data class QuickLog(val amount: Long, val categoryName: String?)`; `fun parseQuickLog(text: String, categories: List<String>): QuickLog?`: accepts Western or Arabic-Indic digits (convert ٠-٩ and ۰-۹), an optional decimal part (`.` or `٫`), the number anywhere in the text; the rest of the text is matched against category names and a keyword map (`فول|طعمية|سندوتش|غدا|عشا|فطار → أكل`, `قهوة|شاي|نسكافيه → قهوة`, `مترو|اتوبيس|تاكسي|أوبر|ميكروباص|توكتوك → مواصلات`, `عيش|خضار|فاكهة|سوبر ماركت|بقالة → بقالة`, `دوا|صيدلية|دكتور → صحة`); null when no positive amount. Tests: `"20 فول وطعمية"` → 2000, أكل; `"٣٥ مواصلات"` → 3500, مواصلات; `"12.5 قهوة"` → 1250, قهوة; `"تاكسي 80"` → 8000, مواصلات; `"شيء"` → null; `"0 شاي"` → null.
- `data class Proverb(val number: Int, val text: String, val line: String, val set: ProverbSet, val rare: Boolean)`, `enum class ProverbSet(val titleRes) { SAVING, SPENDING, DEBT, WORK, PEOPLE, PATIENCE }` from the six headings in `14-PROVERBS.md`; `object ProverbCatalog { val all: List<Proverb> }` with all 50 cards **verbatim** (proverb text before " — ", line after). Rare numbers = `{45, 49, 50}` (14-PROVERBS "Rare cards"). Card colour per set: SAVING turquoise, SPENDING yellow, DEBT carnelian, WORK sky, PEOPLE lilac, PATIENCE turquoise (Proverb mock uses turquoise; a builder's call, recorded as a ruling).
- `nextProverb(collected, seed, size, exclude: Set<Int> = emptySet())` skips `exclude`; callers pass the rare set. `PROVERB_SET_SIZE` now reads `ProverbCatalog.all.size` (remove the shortcut comment). Tests: `ordinaryDeckSkipsRare`, `allCollected` (all 47 ordinary collected → null), `catalogHas50Unique`.
- `TicketRepository.closeDay` awards rare 45 when FULL_WEEK is newly earned and rare 50 when the streak reaches 30 (insert both into `proverb_collected`; the reward shows the rare one instead of the ordinary card that day). Test `fullWeekAwardsRare45`.

- [ ] Steps: tests, implement, push, CI green, commit `feat(v3): quick-log parser and proverb catalog`.

## Task 16: App shell and navigation

**Files:** create `ui/AppRoot.kt`, `ui/nav/Routes.kt`, `feature/placeholder/TabPlaceholder.kt`; modify `MainActivity.kt`, `MasroufiApplication.kt`, `res/values*/strings.xml`; test `AppRootTest.kt`.

**Interfaces – Produces:**
- `@Serializable` routes: `Home`, `CloseDay(date: Long /*epoch day*/)`, `ProverbReward(date: Long)`, `Gallery` (debug only), `RollTab`, `PlanTab`, `PeopleTab`.
- `AppRoot()` = `MasroufiTheme { Scaffold(bottomBar = TzTabBar) { NavHost } }`; tab switches cross-fade 140 ms (`05-MOTION.md`); pushed screens slide from the leading edge with `move`. Tab bar hidden on CloseDay/ProverbReward. The + opens the add sheet (hosted by `AppRoot` so every tab can open it).
- Roll/Plan/People tabs show `TabPlaceholder(titleRes)`: the tab's title in `TzType.titleTab` and Qersh sleepy. No invented copy beyond the tab titles; in debug builds a TextLink "Gallery" on the Plan placeholder opens the gallery.
- `MainActivity` sets `AppRoot()`; the gallery shortcut comment is removed.
- `MasroufiApplication` launches `SeedData.ensureSeeded()` on an application `CoroutineScope(SupervisorJob() + Dispatchers.IO)` injected via Hilt.
- Test: `tabsSwitchAndPlusOpensSheet` (Robolectric Compose with fake/in-memory repos via a Hilt test module or by constructing `AppRoot` with injected ViewModels — choose the lightest that works under Robolectric; record the choice).

- [ ] Steps: implement, test, push, CI green, commit `feat(v3): app shell and navigation`.

## Task 17: Home (اليوم)

**Files:** create `feature/home/HomeViewModel.kt`, `feature/home/HomeScreen.kt`, `feature/home/HomeSections.kt` (header, quick log field, repeat chips, week strip, jar card, cycle card, proverb teaser, skeleton), `feature/home/Greeting.kt`; tests `HomeViewModelTest.kt`, `HomeScreenTest.kt`.

**Interfaces – Produces:**
- `HomeViewModel(homeRepo, ticketRepo, entryRepo, settings, clock, haptics-free)`: on init and on every `ON_START` (ProcessLifecycleOwner) calls `ticketRepo.printToday()`; also schedules a coroutine that waits until the next local midnight (from the clock) and calls `printToday()` again (Review Focus 1). Exposes `StateFlow<HomeUiState>` with `Loading` | `Ready(…)` | `Error`.
- Sections exactly as `Main.dc.html` top to bottom (04-SCREENS "Main"): header (date in Arabic "الخميس 12 نوفمبر" via `DateTimeFormatter.ofPattern("EEEE d MMMM", Locale("ar"))` with Western digits, greeting by hour from `07-COPY.md` "Greetings", streak pill → album route is Phase 8 so the pill is not clickable yet); the `Ticket` with label "تذكرة اليوم · صالحة ليوم واحد", amountLabel "معاك النهارده", left = ticket − spent, sub-line "من 240 · صرفت 84 في 3 مرات" (before the first entry "لسه مصرفتش حاجة النهارده"; over the ticket "عدّيت تذكرة النهارده بـ 35. اتوزّعوا على باقي الأيام." — on the cycle's last day without the second sentence, foundation ruling 14), caption "كل تسجيلة = خُرم في التذكرة", stub "اقطعها آخر اليوم / واللي يفضل يروح الحصّالة" + "قفّل اليوم" → CloseDay; quick log field (placeholder "اكتب: 20 فول وطعمية", IME action logs via `parseQuickLog`; mic launches `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` with `EXTRA_LANGUAGE = "ar-EG"` and feeds the result through the parser; if the parse fails, opens the add sheet with the text as the note); "بتتكرر معاك · دوسة واحدة تخرم" with up to 3 repeat chips (one tap logs, `Confirm` haptic; hidden when none); "أسبوعك" with 7 stubs Saturday first (closed under → turquoise "+leftover", over → carnelian "-over", today yellow-outlined "النهارده", future dashed, no row → dashed) and the count line "N تذاكر مقفولة تحت الحد"; jar card ("الحصّالة", total, gold line hidden — Plus/price not available yet); cycle card ("13 يوم لحد المرتب", progress bar through the cycle, line "معاك 3,120 جنيه. لو كمّلت على تذكرة اليوم، هيفضل معاك حوالي 620 يوم 25." only when the projection ≥ 0, end-of-month link hidden until Phase 9); proverb teaser ("مثل النهارده مقفول / قفّل يومك وافتح كارت جديد · معاك 17 من 50" — uses the real set size). The season card is omitted (Phase 6).
- After closing: the ticket area shows tomorrow's preview "تذكرة بكره: 240" (computed with `computeTicket` from current balances and tomorrow's days left; tomorrow's carry if destination was TOMORROW) and the earned proverb card (small `ProverbCard`, tap → ProverbReward).
- End-of-month row: the ticket shows 0 with the carnelian line from `08-ONBOARDING.md` edge case "اللي معاك أقل من اللي لازم يتدفع بـ X. ولا يهمك، نرتّبها سوا." (X = −eomPool, only when eomPool < 0; when eomPool ≥ 0 but floors to a zero ticket, the sub-line reads "لسه مصرفتش حاجة النهارده"). Full end-of-month mode is Phase 9.
- First-day state: with zero money (fresh seed) Home shows the end-of-month variant — acceptable until onboarding (Phase 4). Debug builds add a "Sample data" TextLink in the gallery that sets opening 587000 on الكاش, payday Day(25) and one recurring bill 275000 so the mock's 240 ticket appears (developer tool; debug only).
- Show the `Ticket` only once the row has loaded (skeleton before), so the punch never replays on open (carry-forward).
- Skeleton per `EmptyStates.dc.html` (ticket outline + blank number block, rows with round tile and two bars; Raised/Line colours; 0.6→1.0 opacity pulse 900 ms; no pulse under reduced motion).
- Tests: `HomeViewModelTest` — `printsOnStart`, `midnightReprintsTicket` (advance the TestClock past midnight + dispatcher time → today's row is the new date), `repeatChipLogsOnce`, `quickLogParses`, `overTicketSubLine`, `lastDayOmitsSpreadSentence`; `HomeScreenTest` — skeleton then content, sub-line texts for 0/3 entries, week strip colours by status.

- [ ] Steps: implement, tests, push, CI green, commit `feat(v3): home screen`.

## Task 18: Add sheet (سجّل) and the punch

**Files:** create `feature/add/AddSheet.kt`, `feature/add/AddViewModel.kt`, `feature/add/Keypad.kt`, `feature/add/AmountInput.kt`; tests `AmountInputTest.kt`, `AddViewModelTest.kt`, `AddSheetTest.kt`.

**Interfaces – Produces:**
- `AmountInput` (pure): digit/point/backspace reducer to a string; max 8 integer digits and 2 decimals; `toPiastres(): Long` exact (no Double). Tests `keypadEdges`: "12.5" → 1250, "0" → 0 (invalid), "." alone → 0, 9th integer digit ignored, third decimal ignored.
- Sheet layout exactly per `AddPunch.dc.html`: `TzSheet`; segmented track (Night 44 dp track per the mock, thumb yellow) صرفت / جالي / حوّلت; close button (`TzIcons.close`, "اقفل"); amount Lalezar 84 yellow + "جنيه" 22 text2 (rolls with `Odometer`); live line "هيفضل في تذكرة النهارده 131" (left after this spend; for income "تذكرة بكره هتكبر"; for transfer the line reads the from/to pockets); category chips (last used first, 48 dp, selected = turquoise 2 dp border on turquoiseDim; icon + name); pocket button "من · الكاش" (opens a small `TzSheet` list of pockets) and "ضيف ملاحظة" (inline text field, 120 chars); for transfer two pocket pickers and an optional "رسوم" amount (fee) field; round-up line "قرّبها لـ 30 و 5 جنيه فكة للحصّالة" with a `TzToggle` only when Plus is on and the step > 0 (Plus off → hidden); 3-column LTR keypad (56 dp keys, Lalezar 26, "." and backspace "امسح"); primary button "اخرم التذكرة" (income "ضيف للفلوس", transfer label from 07-COPY or "حوّل" if absent — check `07-COPY.md` and `AddPunch` first; if no word exists, record it as a builder's call). Amount empty → button reads "اكتب المبلغ" and a tap shakes + Reject + the amount pulses (`05-MOTION.md`).
- The sheet rises from the + (`05-MOTION.md` "Anything → add sheet"): the screen behind scales to 0.96 and dims while the sheet is open (implement in `AppRoot` via a shared `sheetOpen` state; M3 sheet keeps its own spring — foundation ruling).
- **Punch moment** (`05-MOTION.md` §1): on "اخرم التذكرة": Confirm haptic on the same frame, sheet closes, Home's ticket punches the next hole (Ticket already animates on `holes` increase), number rolls down (Odometer). The ViewModel emits the optimistic new `spent`/`holes` to Home's state before `entryRepo.log` returns; on failure it rolls back and shows the feedback line. First entry of the day shows Qersh's bubble "أول خُرم. يوم جديد." for 3 s.
- Feedback line in place (not a toast) under the ticket for 3 s: "اتخرمت. فاضلك 131 النهارده." / over: "اتسجّلت. عدّيت تذكرة النهارده بـ 35، ووزّعتهم على باقي الأيام."
- Double-tap guard 400 ms (Review Focus 2). Tests: `doubleTapLogsOnce`, `incomeLabelAndLine`, `transferNeedsTwoPockets`, `zeroAmountRejects` (Reject haptic, no entry), `roundUpLineOnlyWithPlus`, `optimisticRollbackOnFailure` (repo throws → state restored, error line shown).

- [ ] Steps: implement, tests, push, CI green, commit `feat(v3): add sheet and the punch`.

## Task 19: Close the day (قفّل اليوم) and the tear

**Files:** create `feature/close/CloseDayScreen.kt`, `feature/close/CloseDayViewModel.kt`; tests `CloseDayViewModelTest.kt`, `CloseDayScreenTest.kt`.

**Interfaces – Produces:**
- Layout per `CloseDay.dc.html`: back button, date; title "يومك خلص على خير" + "اقطع التذكرة وشوف الباقي هيروح فين."; the ticket top ("تذكرة الخميس", "فضل معاك", leftover Lalezar 88, holes 24 dp, line "صرفت 168 في 6 مرات · أكبر حاجة: بقالة 90" — biggest entry by amount with its category); the torn stub "كعب التذكرة +72" rotated −3°; "الـ 72 يروحوا فين؟" with three radio cards: الحصّالة ("تبقى 1,912" = jar + leftover; the gold half of the mock's line is omitted until Plus gold), تذكرة بكره ("الجمعة تبقى 312 بدل 240" from tomorrow's preview with and without the carry), ظرف الطوارئ ("فيه 600 دلوقتي" = emergency pot total); preselected = settings default; button "اقطع التذكرة".
- States (04-SCREENS): leftover 0 → no destinations, copy "قفلتها على الشعرة. شاطر."; over → "عدّيت بـ 35. ولا يهمك، هوزّعهم على الـ 12 يوم الجايين (3 جنيه في اليوم)." (overspendPerDay; on the last cycle day only the first sentence) and the button reads "اقطعها وكمّل".
- **Tear moment** (`05-MOTION.md` §2): press-and-hold or tap; stub tilts up to 3° following the finger with a Tick at start; on release the stub moves 16 dp down and rotates to −3° (`reward` spring) with the Heavy haptic on separation; "+72" travels on a short arc into the chosen destination card, which pulses turquoise once and counts up (Tap haptic on landing); then cross-fade to ProverbReward. Reduced motion: still image, same haptics.
- `closeDay` runs once (Review Focus 3): the ViewModel guards re-entry, and the repository is already idempotent. Tests: `tearTwiceOneCard`, `zeroLeftoverHidesDestinations`, `overCopyUsesSpread`, `destinationDefaultsFromSettings`.

- [ ] Steps: implement, tests, push, CI green, commit `feat(v3): close the day and the tear`.

## Task 20: Proverb reward (مثل اليوم), card flip, share

**Files:** create `feature/proverb/ProverbRewardScreen.kt`, `feature/proverb/ProverbRewardViewModel.kt`, `feature/proverb/ProverbShare.kt`, `res/xml/file_paths.xml`; modify `AndroidManifest.xml` (FileProvider `${applicationId}.files`, cache path `share/`); tests `ProverbRewardTest.kt`.

**Interfaces – Produces:**
- Layout per `Proverb.dc.html`: "التذكرة رقم 47 اتقطعت" (number = count of hand-closed tickets), "كسبت مثل جديد", the tilted `ProverbCard` (set title, "18 / 50", proverb, line + " والنهارده حوّشت 72." when leftover > 0 to the jar), three stat tiles (+72 للحصّالة / N يوم سلسلة التذاكر / k / 7 أيام الأسبوع), "شارك المثل" (secondary) and "تمام، لبكره" (primary → Home). All collected → random favourite + "معاك الألبوم كله"; rare card → the card with a stamp (`Stamp`).
- **Card flip** (`05-MOTION.md` §3): enters face-down 20 dp below, flips on the Y axis with `reward` (one overshoot), Milestone haptic as it settles; stat tiles count up 80 ms apart. Reduced motion: still card + Milestone.
- **Share** (`14-PROVERBS.md` "Sharing"): render the card (proverb, number, small "مصروفي" mark, no amounts/name/streak) to a bitmap via `GraphicsLayer.toImageBitmap()`, write PNG to `cacheDir/share/`, share via `FileProvider` + `Intent.ACTION_SEND` chooser.
- Tests: `showsCardNumberAndSetSize`, `allCollectedShowsFavourite`, `shareIntentHasPngUri` (Robolectric shadow of startActivity).

- [ ] Steps: implement, tests, push, CI green, commit `feat(v3): proverb reward, card flip and share`.

## Task 21: New-ticket moment, end-to-end check, schema baseline

**Files:** modify `feature/home/HomeScreen.kt`; create `app/src/test/.../DailyLoopTest.kt`; add `app/schemas/com.masroufi.app.data.db.TazkaraDatabase/1.json` (downloaded from the green CI run's `room-schemas` artifact — the owner OKs the download as part of CI-only builds) and remove the "schema may change freely" shortcut comment; update the carry-forward doc.

**Interfaces – Produces:**
- **New ticket** (`05-MOTION.md` §4): when Home shows a ticket whose date differs from the last one shown (first open of the day), it slides down from under the header: 40 dp in three steps with a Tick per step, then Qersh pops over the corner; 350 ms. Remember the last shown date in `SavedStateHandle`/DataStore key `lastTicketShown` (DataStore, so it survives restarts). Reduced motion: no slide, one Tick.
- `DailyLoopTest` (Robolectric, in-memory DB, TestClock 2026-11-12 09:00 Cairo, sample data: opening 587000, payday 25, bill 275000): Home shows 240; log three spends (5000, 8000, 9800) via the add sheet — after each, holes increase by 1 and the number rolls to 190, 110, 12; the third logged again via a repeat chip counts as a fourth hole; close day → CloseDay shows the leftover; tear to JAR → ProverbReward shows a card; back to Home shows "تذكرة بكره". This is Prompt 3's "done means".
- Carry-forward doc: tick what Phase 3 closed, list anything new.

- [ ] Steps: implement, test, download the schema artifact and commit it, push, CI green, commit `feat(v3): new-ticket moment and daily-loop check`.
