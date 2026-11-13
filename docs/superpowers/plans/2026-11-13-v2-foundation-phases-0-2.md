# Masroufi v2 foundation (Phases 0–2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A clean v2 app on branch `v2` that builds on CI, ships the full Tazkara design system with a gallery screen, and implements the storage and every rule of "The daily ticket", "Closing the day", "Streak, levels, stamps", "Round-ups" and "Envelopes", all unit-tested.

**Architecture:** Single `:app` module. Pure rules in `domain/` (no Android types, plain JUnit). Room + DataStore in `data/`. Compose design system in `ui/theme` and `ui/components`. One stored `DayTicket` row per day is the summary Home and widgets will read.

**Tech Stack:** Kotlin 2.2.21, AGP 8.7.3, Compose BOM 2025.10.01, Room 2.7.2, Hilt 2.57.2, DataStore 1.1.1, kotlinx.serialization, JUnit 4 + Robolectric 4.14.1. Gradle 8.11.1 on CI only.

**Spec:** `docs/superpowers/specs/2026-11-13-masroufi-v2-tazkara-design.md`, which defers to `masroufi-tazkara-handoff/` (`03-DESIGN-SYSTEM.md`, `05-MOTION.md`, `06-HAPTICS.md`, `11-DATA-AND-RULES.md`, `13-ICON.md`, `screens/*.dc.html`).

## Global Constraints

- Package and application ID `com.masroufi.app`. Database file `tazkara.db`. minSdk 26, compile/target 35, JVM 17.
- No `INTERNET` permission. No analytics, ads, tracking, OkHttp, Coil, Backdrop, material-icons-extended.
- Money is `Long` piastres everywhere. Divide only with explicit floor/ceil; never `Double` for money.
- Default resources are Arabic (`values/`), English in `values-en/`. `android:supportsRtl="true"`. Digits Western.
- Dark only: every surface uses the tokens in Task 2; no light theme, no dynamic colour.
- Time comes from an injected `java.time.Clock`; no `LocalDate.now()` without it.
- Verification is CI only: a task is done when `git push origin v2` produces a green "Android CI" run (unit tests + debug + release APKs). Read failures from the run's "Summarize failures" annotation. The owner checks run results on GitHub; ask the owner for the run status if it cannot be read.
- Copy: never invent user-facing words. Gallery labels may be plain developer text (debug only).

## Review Focus

1. **Payday on the 29th–31st in a short month** → payday falls on the month's last day; days left never 0 or negative. Pinned in Task 8 (`payday31InFebruary`, `daysLeftAtLeastOne`).
2. **App not opened for several days** → only the last printed open ticket auto-closes; gap days get no ticket and no jar money (design call 10). Pinned in Task 13 (`gapDaysGetNoTicketAndNoJarMoney`).
3. **Leftover sent to "tomorrow's ticket"** must not be counted twice (it is still in the pockets). Pinned in Task 9 (`carriedInNotDoubleCounted`).
4. **Logging the same entry twice / editing an entry on a closed day** → the closed day's spent and its jar movement both follow; the jar never goes negative. Pinned in Task 13 (`editOnClosedDayAdjustsJarMovement`, `leftoverNeverNegative`).
5. **Opening the app twice in one day / across midnight while running** → `printToday` is idempotent; one ticket per date (unique index). Pinned in Task 13 (`printTodayIsIdempotent`).

---

## File structure (created by this plan)

```
.github/workflows/android.yml            (modify: push on main and v2)
app/build.gradle.kts, gradle/libs.versions.toml   (modify)
app/src/main/AndroidManifest.xml
app/src/main/java/com/masroufi/app/
  MasroufiApplication.kt, MainActivity.kt
  core/Money.kt, core/PayCycle.kt
  domain/TicketRules.kt, domain/CloseDayRules.kt, domain/ProgressRules.kt,
  domain/RoundUp.kt, domain/EnvelopeRules.kt, domain/ProverbDeck.kt
  data/db/TazkaraDatabase.kt, data/db/Converters.kt, data/db/Entities.kt, data/db/Daos.kt
  data/prefs/SettingsStore.kt
  data/repo/TicketRepository.kt, data/repo/EntryRepository.kt
  entitlements/Entitlements.kt
  di/AppModule.kt
  ui/theme/Tokens.kt, ui/theme/Type.kt, ui/theme/Theme.kt
  ui/motion/Springs.kt, ui/motion/Press.kt, ui/motion/ReduceMotion.kt
  ui/haptics/Haptics.kt
  ui/icons/TzIcons.kt
  ui/components/Buttons.kt, Chip.kt, Segmented.kt, Card.kt, Row.kt, Toggle.kt, Sheet.kt, TabBar.kt,
                 ProgressBar.kt, Odometer.kt
  ui/paper/Ticket.kt, Stub.kt, EnvelopeCard.kt, Stamp.kt, ProverbCard.kt, Qersh.kt, Guilloche.kt
  feature/gallery/GalleryScreen.kt
app/src/main/res/font/lalezar.ttf, alexandria.ttf
app/src/test/java/com/masroufi/app/... (one test file per rules file, plus repo tests)
licenses/Lalezar-OFL.txt, licenses/Alexandria-OFL.txt
README.md (rewrite)
```

---

## Phase 0

### Task 1: Clean branch, skeleton app, CI on v2

**Files:**
- Delete: `app/src/**` except `res/drawable/ic_launcher_background.xml`, `ic_launcher_foreground.xml`, `ic_launcher_monochrome.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/locales_config.xml`; delete `app/schemas/`, `masroufi-v2-design/`, `PRODUCT.md`, `licenses/IBMPlexSansArabic-OFL.txt`, `licenses/ReadexPro-OFL.txt`.
- Add (commit as-is): `masroufi-tazkara-handoff/`, the design doc, this plan.
- Create: `MasroufiApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `res/values/strings.xml` (`app_name` = `مصروفي`), `res/values-en/strings.xml` (`app_name` = `Masroufi`), `res/values/themes.xml`.
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.github/workflows/android.yml`, `README.md`.

- [ ] **Step 1:** `git rm -r` the files above; keep v1 launcher drawables with a comment in `ic_launcher.xml`: `<!-- shortcut: v1 icon until Phase 13 draws the Tazkara icon from 13-ICON.md -->`.
- [ ] **Step 2:** Remove `okhttp`, `okhttp-mockwebserver`, `coil-compose`, `backdrop`, `compose-icons-extended` from the catalog and from `app/build.gradle.kts`. Add `compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }` and `compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }` (test-only). Remove the okhttp `-dontwarn` lines from proguard.
- [ ] **Step 3:** Manifest: permissions `POST_NOTIFICATIONS`, `USE_BIOMETRIC` only. `allowBackup=false`, `fullBackupContent=false`, `dataExtractionRules`, `localeConfig`, `supportsRtl=true`, theme `Theme.Masroufi`, `MainActivity` launcher, `enableOnBackInvokedCallback=true`, the `AppLocalesMetadataHolderService` block from v1, WorkManager initializer removal from v1 (Hilt worker factory comes later; keep the `tools:node="remove"` block only once a worker exists, so omit it now).
- [ ] **Step 4:** `Theme.Masroufi` parent `Theme.AppCompat.NoActionBar`, `android:windowBackground` `#0A1033`, status and navigation bars transparent. `MainActivity : AppCompatActivity`, `@AndroidEntryPoint`, `enableEdgeToEdge()`, `setContent` shows a full-screen `Box` in `#0A1033` with `stringResource(R.string.app_name)` centred in white. `MasroufiApplication` is `@HiltAndroidApp`.
- [ ] **Step 5:** CI: `on.push.branches: [ main, v2 ]`. Rewrite `README.md` to a short v2 description pointing at the handoff and design doc, keeping the "Building" and "Release signing" sections.
- [ ] **Step 6:** Commit `chore(v2): start Tazkara rebuild from an empty app` and `git push -u origin v2`.
- [ ] **Step 7:** CI run on `v2` is green; the debug APK opens to the lapis screen with "مصروفي".

---

## Phase 1: design system

All components read sizes from the mocks (1 px = 1 dp). When a value below and a mock disagree, the mock wins.

### Task 2: Fonts, colour tokens, type, shapes

**Files:** `res/font/lalezar.ttf`, `res/font/alexandria.ttf`, `licenses/*-OFL.txt`, `ui/theme/Tokens.kt`, `ui/theme/Type.kt`, `ui/theme/Theme.kt`.

**Interfaces – Produces:**
- `object Tz` with every colour of `03-DESIGN-SYSTEM.md` as `Color`: `night, surface, raised, line, dashed, text, text2, text3, yellow, yellowInk (#3D2F00), yellowInk2 (#5C4700), yellowRule (#B38600), yellowRule2 (#E3AB00), yellowDim, turquoise, turquoiseInk, turquoiseDim, turquoiseDimText (#D6F7F3), carnelian, carnelianText, carnelianDim, carnelianInk, sky, lilac, qershRing (#C99400), qershOnYellow (#FFE08A)`.
- `object TzRadius { chip=18.dp, card=24.dp, hero=28.dp, sheetTop=32.dp, tile=14.dp, tabBar=26.dp }`, `object TzSpace { s4…s24, gutter=16.dp }`.
- `val Lalezar: FontFamily`, `val Alexandria: FontFamily` (variable font; weights 400/500/600/700 via `FontVariation.Settings(FontVariation.weight(w))`).
- `object TzType` with `TextStyle`s: `ticket` (Lalezar 104 sp, lineHeight 0.95 em, letterSpacing −2 sp), `hero` (56), `card` (32), `rowNumber` (20), `titleTab` (30), `titlePushed` (22), `cardHeading` (20), `button` (20), `body` (Alexandria 15, lineHeight 1.7 em), `label` (13, w500), `caption` (12). All with `fontFeatureSettings = "tnum"`.
- `@Composable fun MasroufiTheme(content)` providing `LocalLayoutDirection` from the locale (no override), `LocalReduceMotion`, `LocalHaptics`, and a `MaterialTheme` dark colour scheme mapped from `Tz` (only so platform widgets do not show purple).

- [ ] **Step 1:** Download `ofl/lalezar/Lalezar-Regular.ttf`, `ofl/alexandria/Alexandria[wght].ttf` and each folder's `OFL.txt` from `https://github.com/google/fonts` (raw URLs) into the paths above.
- [ ] **Step 2:** Write `TokensTest`: `assertEquals(Color(0xFF0A1033), Tz.night)`, `assertEquals(Color(0xFFFFC72C), Tz.yellow)`, `assertEquals(Color(0xFF35E0D0), Tz.turquoise)`, `assertEquals(Color(0xFFFF6B4A), Tz.carnelian)`; `assertEquals(104.sp, TzType.ticket.fontSize)`.
- [ ] **Step 3:** Implement; push; CI green.

### Task 3: Motion, press, reduce motion, haptics

**Files:** `ui/motion/Springs.kt`, `ui/motion/Press.kt`, `ui/motion/ReduceMotion.kt`, `ui/haptics/Haptics.kt`, test `ui/motion/SpringsTest.kt`.

**Interfaces – Produces:**
- `enum class TzSpring(val damping: Float, val response: Float) { Press(1f,.15f), Snappy(1f,.24f), Move(1f,.38f), Sheet(.86f,.32f), Reward(.62f,.45f) }` and `fun <T> TzSpring.spec(): SpringSpec<T>` with `stiffness = (2π / response)²`, `dampingRatio = damping`.
- `val LocalReduceMotion: ProvidableCompositionLocal<Boolean>`; true when `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`. `fun <T> motionSpec(spring: TzSpring): AnimationSpec<T>` returns `tween(120)` when reduced.
- `fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float): Modifier`: scales on touch-down with `Press` spring. Values per `05-MOTION.md`: primary 0.97 (+ brightness −6%), card/row 0.98, chip/key/stepper 0.94, tab icon 0.9, yellow + 0.92.
- `enum class HapticLevel { Tick, Tap, Confirm, Heavy, Milestone, Reject }`, `interface Haptics { fun perform(level: HapticLevel) }`, `val LocalHaptics`. Android impl uses `View.performHapticFeedback`: Tick=`CLOCK_TICK`, Tap=`VIRTUAL_KEY`, Confirm=`CONFIRM` (API 30+, else `VIRTUAL_KEY`), Heavy=`LONG_PRESS`, Reject=`REJECT` (API 30+, else `LONG_PRESS`), Milestone=`VibrationEffect.createWaveform(longArrayOf(0,20,60,20,60,45), intArrayOf(0,120,0,120,0,255), -1)` only when `vibrator.hasAmplitudeControl()`, else nothing. Respects the system touch-feedback setting (`performHapticFeedback` default flags) and an in-app `enabled` flag (wired to settings in Phase 11).

- [ ] **Step 1:** `SpringsTest.pressStiffness`: `assertEquals((2*PI/0.15).pow(2).toFloat(), TzSpring.Press.stiffness, 0.5f)`; `rewardOvershoots`: `assertTrue(TzSpring.Reward.damping < 1f)`.
- [ ] **Step 2:** Implement; push; CI green.

### Task 4: Icon family

**Files:** `ui/icons/TzIcons.kt`.

**Interfaces – Produces:** `object TzIcons` with `ImageVector`s built from the `<path>` data in the mocks (24 grid, stroke 1.9, round caps/joins, no fill). Tab icons need `outline` and `filled` pairs: `today`, `roll`, `plan`, `people`, `add`. Others (outline only): `flame, mic, wallet, jar, season, calendar, search, chevron, close, back, check, lock, fingerprint, delete, share, bell, settings, gold, envelope, swap`. Stroke 2.2 is used at ≤ 18 dp by a `TzIcon(vector, size, contentDescription)` composable that picks the stroke variant.

- [ ] **Step 1:** Extract every distinct `<svg viewBox="0 0 24 24">` path from `screens/*.dc.html` (`grep -o '<path d="[^"]*"'`), name each by its `aria-label` or use, and convert with `PathParser`-compatible `addPathNodes`.
- [ ] **Step 2:** `TzIconsTest.allIconsAre24Grid`: every vector in `TzIcons.all` has `viewportWidth == 24f && viewportHeight == 24f`.
- [ ] **Step 3:** Push; CI green.

### Task 5: Base components

**Files:** `ui/components/*.kt` as listed in the file structure.

**Interfaces – Produces (all take `modifier: Modifier = Modifier` and apply `pressScale` + a haptic):**
- `PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, height: Dp = 58.dp)`: yellow fill, night text, `TzType.button`, full pill. Disabled = still yellow at 40% alpha; tapping it shakes 6 dp horizontally and fires `Reject` (never silent).
- `SecondaryButton(text, onClick)`: 1.5 dp `Tz.line` outline. `TextLink(text, onClick)`: yellow, 13 sp, 48 dp touch height.
- `TzChip(label: String, selected: Boolean, onClick, style: ChipStyle = ChipStyle.Choice)`; `ChipStyle.Filter` selected = text-on-night fill, `Choice` selected = yellow. Height 44 dp; select animates scale 1.0→1.04→1.0 with `Snappy`; haptic `Tick`.
- `Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit)`: surface track, line border, yellow thumb sliding with `Snappy`, Lalezar 17.
- `TzCard(padding: Dp = 16.dp, onClick: (() -> Unit)? = null, content)`: surface, 1 dp line border, radius 24.
- `TzRow(leading: @Composable () -> Unit, title: String, caption: String?, trailing: @Composable () -> Unit, onClick)`: 44 dp leading tile, title 15/600, caption 12 `text2`.
- `TzToggle(checked: Boolean, onCheckedChange, contentDescription: String)`: 48×28 track, on = turquoise with dark knob, off = dashed colour; knob `Snappy`; `Tick`.
- `TzSheet(onDismiss, content)`: surface, top radius 32, 40×5 grabber, night scrim; drag-to-dismiss with `Sheet` spring and fling velocity.
- `TzTabBar(selected: TzTab, onSelect: (TzTab) -> Unit, onAdd: () -> Unit)`; `enum class TzTab { Today, Roll, Plan, People }`. Floating, surface, radius 26, height 72, 56 dp yellow + in the middle; selected = filled icon in yellow + bold label; labels `اليوم`, `الدفتر`, `الخطة`, `الناس`; + has `contentDescription = "سجّل عملية جديدة"`.
- `TzProgressBar(parts: List<Pair<Float, Color>>, height: Dp = 8.dp)`: round ends, line track, 3 dp gaps between parts, widths spring with `Move`.
- `Odometer(valuePiastres: Long, style: TextStyle, showPiastres: Boolean = false)`: each digit rolls (down when the value fell, up when it rose), 300 ms, continues from current digits if retargeted; digits LTR inside RTL; reduce-motion = 120 ms fade.

- [ ] **Step 1:** Robolectric Compose test `ComponentsTest`: `disabledPrimaryButtonFiresReject` (fake `Haptics` records `Reject`, `onClick` not called); `tabBarAddHasLabel` (node with content description `سجّل عملية جديدة` exists); `odometerShowsWholePounds` (`Odometer(15600)` shows text `156`).
- [ ] **Step 2:** Implement; push; CI green.

### Task 6: Paper components and Qersh

**Files:** `ui/paper/*.kt`.

**Interfaces – Produces:**
- `Ticket(label: String, leftPiastres: Long, subLine: String, holes: Int, caption: String, stub: @Composable () -> Unit, showQersh: Boolean = true)`: yellow, radius 28, guilloche top-start, Qersh 76 dp peeking at top (−40 dp), dashed `yellowRule` perforation with two 26 dp night notches, up to 8 holes of 26 dp (punched = night fill; unpunched = 2 dp dashed outline) then `+N`. `punchedHoleIndex` animation API: `fun TicketState.punch()` fills the next hole centre-out (120 ms) and recoils the ticket 3 dp with `Reward`. Semantics: whole ticket merges to one `contentDescription` passed by the caller.
- `Stub(tiltDegrees: Float = 2.5f, content)`; `EnvelopeCard(name, color, leftPiastres, bar: Float, line: String?, sealed: Boolean)` with square top corners and a V flap; `Stamp(label: String, color: Color?, tilt: Float = -8f)` (null colour = dashed locked); `ProverbCard(setName, number, total, proverb, line, color)` 300×400, tilted 2°; `Guilloche(color, modifier)` concentric circles r = 30..100 step 14, stroke 1.2.
- `enum class QershMood { Happy, Delighted, Thinking, Surprised, Sleepy, Sad, Waving }`, `Qersh(mood: QershMood, size: Dp, onYellow: Boolean = false)` drawn on the 72 grid from `13-ICON.md` (body r 26 at (36,42), 3 dp night outline, dashed ring r 19 hidden below 56 dp, fin path `M30 16 Q37 -2 50 8 Q44 11 45 20 Z`, eyes r 3.5 at (28,38)/(44,38)); blinks every 4–7 s (random), mood cross-fade 200 ms; no idle motion when reduce-motion.
- `QershBubble(text: String, qershSize: Dp = 64.dp)`: surface bubble, corner nearest Qersh radius 6.

- [ ] **Step 1:** `PaperTest.ticketShowsPlusN`: `Ticket(holes = 11)` shows `+3`; `ticketIsOneTalkBackNode`: merged node has the passed description.
- [ ] **Step 2:** Implement; push; CI green.

### Task 7: Gallery

**Files:** `feature/gallery/GalleryScreen.kt`, modify `MainActivity.kt`.

- [ ] **Step 1:** `GalleryScreen()` is a scrolling list of every Task 5–6 component in every state (enabled/disabled/pressed-preview, selected/unselected, each Qersh mood, ticket with 0/3/11 holes and "over" sub-line, envelope sealed and open, stamp earned/locked, odometer with +/− buttons, a "Plus on" `TzToggle` bound to `Entitlements.debugOverride` from Task 12 once it exists). Sample text in the gallery is taken from the mocks and labelled "عيّنة".
- [ ] **Step 2:** `MainActivity` shows `GalleryScreen` inside `MasroufiTheme` (replaced by Home in Phase 3).
- [ ] **Step 3:** Commit `feat(v2): design system and component gallery`, push, CI green. Ask the owner to install the debug APK and compare the gallery with the mocks; record their corrections as Task 7 fix-ups before Phase 2.

---

## Phase 2: data and rules

Definitions used by every task below:
- **Pool** = Spendable − Reserved.
- **Spendable** = Σ balance of pockets with `countsInTicket && !archived`, primary currency only. Balance = opening + received − spent ± moves (+ moves in, − moves out).
- **Reserved** = Σ unpaid recurring items due on or before the day before next payday + whole jar total + emergency pot total (design call 9). Carried-in money is handled separately by `computeTicket` (Task 9), never inside Reserved.

### Task 8: Money and pay cycle

**Files:** `core/Money.kt`, `core/PayCycle.kt`, tests `core/MoneyTest.kt`, `core/PayCycleTest.kt`.

**Interfaces – Produces:**
- `fun Long.floorToPound(): Long` (to a multiple of 100, toward −∞), `fun Long.ceilToPound(): Long`, `fun Long.ceilDiv(d: Long): Long`, `const val PIASTRES = 100L`.
- `sealed interface Payday { data class Day(val day: Int) /*1..31*/ ; data object LastDay ; data object NotFixed }`.
- `data class PayCycle(val start: LocalDate, val nextPayday: LocalDate)` with `fun daysLeft(today: LocalDate): Int` = days from today through `nextPayday − 1`, minimum 1.
- `fun payCycleFor(today: LocalDate, payday: Payday, lastIncome: LocalDate?): PayCycle`. `Day(d)` clamps to month length; if today's clamped payday == today the cycle starts today. `NotFixed`: start = `lastIncome ?: today`, next = start + 30 days, rolled forward by 30 while ≤ today.

- [ ] **Step 1: Tests:** `floorToPound(15699) == 15600`; `ceilDiv(10, 3) == 4`; `payday25Mid` (today 2026-11-12, Day(25) → next 2026-11-25, `daysLeft == 13`); `paydayToday` (today 2026-11-25 → start 11-25, next 12-25, daysLeft 30); `payday31InFebruary` (today 2027-02-10, Day(31) → next 2027-02-28); `lastDay` (today 2026-11-30, LastDay → start 11-30, next 12-31); `daysLeftAtLeastOne` (today = nextPayday − 1 → 1); `notFixedRolling` (lastIncome 2026-10-01, today 2026-11-12 → start 10-31, next 11-30).
- [ ] **Step 2:** Implement; push; CI green.

### Task 9: The daily ticket rule

**Files:** `domain/TicketRules.kt`, test `domain/TicketRulesTest.kt`.

**Interfaces – Produces:**
- `sealed interface TicketCalc { data class Printed(val amount: Long, val base: Long, val carriedIn: Long) ; data class EndOfMonth(val pool: Long, val daysLeft: Int) }`.
- `fun computeTicket(spendable: Long, reserved: Long, carriedIn: Long, daysLeft: Int): TicketCalc`. `reserved` excludes `carriedIn`; the carry is taken out of the pool before dividing, then added: `base = floorToPound((spendable − reserved − carriedIn) / daysLeft)`, `amount = base + carriedIn`. If `spendable − reserved − carriedIn ≤ 0` → `EndOfMonth(pool = spendable − reserved, daysLeft)`.
- `fun spentToday(entries: List<EntryForDay>): Long`, `data class EntryForDay(val kind: EntryKind, val amount: Long, val roundUp: Long, val countsInTicket: Boolean, val recurringId: Long?)`: sums `amount + roundUp` for `SPENT` entries with `countsInTicket && recurringId == null` (design call 1).
- `fun overspendPerDay(overspend: Long, daysLeft: Int): Long = overspend.ceilDiv(daysLeft)` rounded to pounds up (copy "3 جنيه في اليوم").

- [ ] **Step 1: Tests:** `basic` (spendable 587000, reserved 275000, carried 0, days 13 → `Printed(amount = 24000)`, the mock's 240 جنيه); `roundsDownToPound` (pool 100050, days 1 → 100000); `carriedInNotDoubleCounted` (spendable 100000, reserved 0, carried 7200, days 10 → base 9200, amount 16400); `zeroPoolIsEndOfMonth`; `negativePoolIsEndOfMonth` (spendable 100000, reserved 140000 → EndOfMonth(−40000)); `fixedBillEntriesDoNotPunch` (SPENT 5000 with recurringId → spent 0); `roundUpCountsAsSpent` (SPENT 2500 + roundUp 500 → 3000); `underTilePocketExcluded`; `overspendSpread` (3500 over, 12 days → 300).
- [ ] **Step 2:** Implement; push; CI green.

### Task 10: Close day, streak, levels, stamps, proverb deck, round-up

**Files:** `domain/CloseDayRules.kt`, `domain/ProgressRules.kt`, `domain/ProverbDeck.kt`, `domain/RoundUp.kt`, tests for each.

**Interfaces – Produces:**
- `enum class TicketStatus { OPEN, CLOSED_BY_HAND, CLOSED_AUTO }`, `enum class LeftoverDestination { JAR, TOMORROW, EMERGENCY }`.
- `fun leftover(ticket: Long, spent: Long): Long = max(0, ticket − spent)`.
- `data class DayResult(val date: LocalDate, val status: TicketStatus, val ticket: Long, val spent: Long)`.
- `fun streak(days: List<DayResult>, today: LocalDate): Int`: consecutive `CLOSED_BY_HAND` days ending today or yesterday; a missing date or a non-hand close breaks it. No-spend days count.
- `fun level(handClosed: Int): Int` with thresholds `[0, 7, 20, 40, 60, 100, 180, 365]` → 1..8; `val LEVEL_NAMES = listOf("مبتدئ","ماشي","منظّم","مدبّر","مقتصد","أسطى","معلّم","حوت")`; `fun nextLevelAt(handClosed: Int): Int?`.
- `enum class StampKind { FIRST_TICKET, FULL_WEEK, HAT_TRICK, TIGHT_FIST, FIRST_GRAM, SETTLED }`; `fun earnedStamps(days: List<DayResult>): Set<StampKind>` covering the first three only (others in later phases). Full week = all 7 days Sat–Fri `CLOSED_BY_HAND`. Hat-trick = 3 consecutive hand-closed days with `spent ≤ ticket`.
- `fun weekStart(date: LocalDate): LocalDate` (previous-or-same Saturday).
- `fun nextProverb(collected: Set<Int>, seed: Long, size: Int): Int?` = first number in `(1..size).shuffled(Random(seed))` not in `collected`; null when all collected.
- `fun roundUp(amount: Long, stepPounds: Int): Long` = (next multiple of `stepPounds × 100`) − amount; 0 when already a multiple or step is 0.

- [ ] **Step 1: Tests:** `leftoverNeverNegative`; `streakCountsHandClosesOnly` (hand, hand, auto, hand, hand → 2); `streakBreaksOnGap`; `streakIncludesNoSpendDay`; `levelThresholds` (`level(0)==1`, `level(6)==1`, `level(7)==2`, `level(365)==8`); `fullWeekNeedsSatToFri`; `hatTrickNeedsUnder`; `weekStartsSaturday` (2026-11-12 Thursday → 2026-11-07); `proverbDeckStableForSeed`; `proverbDeckExhausted` (null); `roundUp(2300, 5) == 200`; `roundUp(2500, 5) == 0`; `roundUp(2300, 50) == 2700`; `roundUpOff` (step 0 → 0).
- [ ] **Step 2:** Implement; push; CI green.

### Task 11: Envelope rules

**Files:** `domain/EnvelopeRules.kt`, test `domain/EnvelopeRulesTest.kt`.

**Interfaces – Produces:**
- `fun envelopeLeft(monthly: Long, spentThisCycle: Long): Long` (may be negative; UI shows "عدّيت").
- `fun runsOutDay(left: Long, spentThisCycle: Long, daysWithData: Int, today: LocalDate, nextPayday: LocalDate): LocalDate?` = today + ceil(left / (spent / daysWithData)) days; null when `daysWithData < 5`, spent is 0, left ≤ 0, or the date is ≥ nextPayday.
- `sealed interface SplitRule { data class Fixed(val envelopeId: Long, val amount: Long) ; data class Percent(val envelopeId: Long, val percent: Int) }`; `data class SplitResult(val shares: Map<Long, Long>, val unassigned: Long)`; `fun splitIncome(income: Long, rules: List<SplitRule>): SplitResult`: fixed amounts first (stop when income runs out), then percents of what remains, each floored to pounds; the rest is `unassigned` ("من غير ظرف").

- [ ] **Step 1: Tests:** `runsOutBeforePayday` (left 30000, spent 50000 over 10 days, today 11-12, payday 11-25 → 11-18); `hiddenUnderFiveDays`; `hiddenWhenAfterPayday`; `splitFixedThenPercent` (income 1000000, Fixed(a, 300000), Percent(b, 50) → a 300000, b 350000, unassigned 350000); `splitFixedExceedsIncome` (income 100000, Fixed 300000 → a 100000, unassigned 0).
- [ ] **Step 2:** Implement; push; CI green.

### Task 12: Storage, settings, entitlements

**Files:** `data/db/*.kt`, `data/prefs/SettingsStore.kt`, `entitlements/Entitlements.kt`, `di/AppModule.kt`, test `data/db/SchemaTest.kt`.

**Interfaces – Produces:**
- Entities (all with `id: Long` autogen PK, `updatedAt: Long`): `PocketEntity(name, kind: PocketKind {CASH, MOBILE_WALLET, BANK, UNDER_TILE, VAULT}, currency = "EGP", openingBalance, color: Int, archived, countsInTicket)`; `EntryEntity(kind: EntryKind {SPENT, RECEIVED, MOVED}, amount, pocketId, toPocketId?, categoryId?, note? (≤120), happenedAt: Long, loggedAt: Long, roundUp = 0, recurringId?, fee = 0, isFee = false, trackedItemId?, unitPrice?)` with index on `happenedAt`; `CategoryEntity(name, icon, color, parentId?, essential: Boolean)`; `DayTicketEntity(date: LocalDate UNIQUE, amount, base, carriedIn, spent, entryCount, status: TicketStatus, leftover, destination: LeftoverDestination?, proverbNumber?, endOfMonth: Boolean)`; `EnvelopeEntity(name, color, monthly, kind: EnvelopeKind {SPENDING, FIXED, EMERGENCY})` + `EnvelopeCategoryEntity(envelopeId, categoryId)` (composite PK); `RecurringEntity(name, amount, pocketId, scheduleDay: Int, nextDue: LocalDate, envelopeId?, paidThisCycle: Boolean, kind: RecurringKind {BILL, INSTALLMENT, SUBSCRIPTION, GAMEYA})`; `JarMovementEntity(pot: Pot {JAR, EMERGENCY}, amount (signed), source: JarSource {TICKET_LEFTOVER, ROUND_UP, MANUAL, CHANGED_MIND}, goalId?, dayDate: LocalDate?, entryId?, at: Long)`; `GoalEntity(name, target, date: LocalDate?)`; `ProverbCollectedEntity(number PK, collectedOn: LocalDate)`; `StampEntity(kind: StampKind PK, earnedOn: LocalDate)`.
- `Converters`: `LocalDate` ↔ epoch day `Long`; enums by `name`.
- `@Database(version = 1, exportSchema = true)` `TazkaraDatabase`, file `tazkara.db`, no destructive fallback. Shortcut comment: `// shortcut: schema may change freely until Phase 3 ships the first usable build; from then every change adds a Migration.`
- DAOs: one per entity group, `Flow` reads plus `suspend` writes; `DayTicketDao.forDate(date): DayTicketEntity?`, `latestOpenBefore(date)`, `range(from, to): Flow<List<DayTicketEntity>>`; `EntryDao.forDay(start, end)`; `PocketDao.balances(): Flow<List<PocketBalance>>` computed in SQL; `JarDao.total(pot): Flow<Long>`; `RecurringDao.unpaidDueBefore(date): Long`.
- `SettingsStore` (DataStore) with `data class Settings(payday: Payday = Payday.Day(25), leftoverDefault = JAR, roundUpStep: Int = 0, proverbSeed: Long (random on first read, then fixed), onboarded = false)`.
- `interface Entitlements { val isPlus: StateFlow<Boolean> }`; `DebugEntitlements` with `var debugOverride` (debug builds), release returns false until Phase 10.

- [ ] **Step 1:** Robolectric `SchemaTest.dayTicketDateIsUnique` (second insert with same date throws `SQLiteConstraintException`); `balancesIncludeMoves` (cash 100000 opening, spent 2000, moved 5000 to wallet → cash 93000, wallet 5000).
- [ ] **Step 2:** Implement; push; CI green; `room-schemas` artifact contains `1.json`.

### Task 13: Ticket and entry repositories (the edge cases)

**Files:** `data/repo/TicketRepository.kt`, `data/repo/EntryRepository.kt`, test `data/repo/TicketRepositoryTest.kt` (Robolectric, in-memory Room, fixed `Clock`).

**Interfaces – Consumes:** Tasks 8–12. **Produces:**
- `TicketRepository.printToday(): DayTicketEntity`: in one transaction: (1) if a ticket for today exists, return it; (2) auto-close `latestOpenBefore(today)`: status `CLOSED_AUTO`, leftover to `settings.leftoverDefault`, no proverb; (3) compute pool from DAOs and `computeTicket`; carriedIn = previous day's leftover if its destination was `TOMORROW` and that day is yesterday; (4) insert today's row (`endOfMonth = true`, amount 0 when `EndOfMonth`).
- `TicketRepository.closeDay(date: LocalDate, destination: LeftoverDestination): CloseResult` with `data class CloseResult(val leftover: Long, val proverbNumber: Int?, val newStamps: Set<StampKind>)`: status `CLOSED_BY_HAND`, writes a `JarMovementEntity(source = TICKET_LEFTOVER, dayDate = date)` for JAR/EMERGENCY when leftover > 0, assigns `nextProverb`, records new stamps.
- `EntryRepository.log(entry: NewEntry): Long` and `edit(id, NewEntry)`, `delete(id)`: write the entry, apply round-up when `entitlements.isPlus && step > 0 && kind == SPENT` (writes `JarMovementEntity(source = ROUND_UP, entryId)`), then `recalcDay(date)`: recompute that day's `spent`/`entryCount`; if the day is closed with a leftover movement, set the movement amount to the new `leftover(ticket, spent)` (0 deletes it). Income never changes a printed ticket.

- [ ] **Step 1: Tests (fixed clock, payday Day(25), today 2026-11-12):** `printTodayIsIdempotent`; `overTicketSpreadsTomorrow` (spend 3500 over today; tomorrow's base falls by the overspend ÷ 12, floored); `zeroPoolPrintsEndOfMonth`; `paydayTodayStartsNewCycle` (clock 2026-11-25 → days 30); `incomeMidDayKeepsTodaysTicket` (log RECEIVED 100000 after print → today's amount unchanged; tomorrow's print is larger); `editOnClosedDayAdjustsJarMovement` (close with leftover 7200 to JAR, then add SPENT 2000 to that day → movement 5200); `leftoverNeverNegative` (add SPENT 10000 to that day → movement removed, jar total not negative); `missedDayAutoClosesNoCard` (print 11-12, no close, advance clock to 11-13, print → 11-12 `CLOSED_AUTO`, `proverbNumber == null`, streak 0); `gapDaysGetNoTicketAndNoJarMoney` (print 11-12, advance to 11-16, print → only 11-12 and 11-16 rows exist; jar total = 11-12's leftover only); `closedLeftoverToJarLowersTomorrow` (leftover 7200 to JAR → tomorrow's pool lower by 7200); `carryToTomorrow` (leftover 7200 TOMORROW → tomorrow's `carriedIn == 7200`, amount = base + 7200); `roundUpOnlyWithPlus` (Plus off → no ROUND_UP movement; on with step 5, SPENT 2300 → movement 200, spent 2500).
- [ ] **Step 2:** Implement; push; CI green.
- [ ] **Step 3:** Commit `feat(v2): storage and daily ticket rules`; report the full test list and the CI run to the owner.
