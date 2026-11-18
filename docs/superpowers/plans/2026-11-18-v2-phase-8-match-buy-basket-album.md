# Masroufi v2 Phase 8 (match, before you buy, basket, album, profile) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** The rewards and buying screens: the weekly match (ماتش الأسبوع), before you buy (قبل ما تشتري) with the "نِفسي فيه" wishlist, the basket (سلّتك, Plus), the album (ألبومك) with levels and stamps, and the profile (ملفك).

**Spec:** `12-BUILD-PROMPTS.md` Prompt 8; `02-FEATURES.md` §18–21, §25; `11-DATA-AND-RULES.md` ("Streak, levels, stamps", "Weekly match", "Before you buy", "Basket", "Profile"); `04-SCREENS.md` ("Basket", "Album", "Match", "BeforeBuy", "Profile"); mocks `screens/Match|BeforeBuy|Basket|Album|Profile.dc.html`; `05-MOTION.md` §5 (match goal) and "Parking an item"; `06-HAPTICS.md` (goal Confirm / against Tap; hat-trick, new level, full week, stamp = Milestone; park = Confirm); `07-COPY.md` (before-you-buy buttons, album/wishlist/match empty states, the 48-hour line); `09-PREMIUM.md` (match history beyond 4 weeks, basket, installment comparison are Plus).

## Global constraints

As Phases 3–7 (Migrations only, never edit old ones; strings ar + en; copy verbatim; Long piastres; injected Clock; Plus via `Entitlements` + `PlusLocked`; tests that assert ViewModel state after an action wait for it to settle; the public GitHub API allows 60 requests an hour, so poll CI at most once a minute).

## Decisions taken in this plan (owner may override)

1. **Entry points:** the album opens from Home's streak pill (`04-SCREENS` Main 1) and from Settings later; the match opens from Home's "أسبوعك" strip; before you buy is a link on the Plan tab next to الاشتراكات and الثوابت (builder's placement, no mock shows one); the basket opens from the album's "سلّتك والأسعار" row; the profile opens from the album's identity area and from Settings later.
2. **The 48-hour question** is asked in the app: once 48 h pass, the wish shows "عدّى اليومين. لسه عايزها؟" in the list, and Home shows one card with the `07-COPY` line ("عدّى يومين على «…». لسه عايزها؟") until answered. The system notification comes with Phase 11's notifications.
3. **Basket items:** the add sheet gets an optional "item and unit price" row for SPENT entries (collapsed by default; builder's wording). The quantity is amount ÷ unit price. An item is tracked after two logged entries with a unit price for the same normalised name, or when the user adds it from "تابع سعر حاجة تانية". The place shown is the entry's note when there is one; nothing else is invented.
4. **Stamps are written once** (`stamp` table, kind + date earned) when the rule first holds, from the close-day path and the jar/people paths; the album reads the table. Tight fist needs end-of-month mode (Phase 9) and stays locked until then.
5. **Safety ticket** (Plus, §22) waits for Phase 10.
6. **Delete all my data** (profile): asks once, names what goes, then clears the database, `tazkara_settings`, and v1's old database and settings files, and restarts at onboarding. The "save a backup first" offer appears in Phase 11 with backup.
7. **Profile effects now:** main goal reorders Home's cards below the ticket; household numbers scale only the school-season default target and the grocery envelope suggestion; the birthday plan is target ÷ days until the birthday (target typed, default 1,000). The share card (no amounts) uses the share sheet with a rendered image if simple, else text.

---

## Task 40: Progress data — match, album, stamps, profile

- `MatchRepository`: weeks Saturday–Friday; goal for = a day closed with leftover ≥ 0, goal against = closed over; not closed = nothing. This week vs last week (goals for), events per day (with the leftover/overspend amount and "best day"), days left to the whistle, hat-trick state (two in a row today → nudge), league rows (week number, result win/draw/loss, seven dots, saved = Σ leftovers − overspends, not below 0), "won N of last M, longest win run", first-week flag. History beyond 4 weeks behind `Entitlements`.
- `AlbumRepository`: level, name, next level and how many tickets to it (`ProgressRules`), tickets closed by hand, current and longest streak, days under the ticket, proverb cards (collected with number/text, locked count of the set), stamps from the table with dates and counts, recent stubs (last N days with leftover/over).
- Stamp awarding (decision 4): first ticket, full week, hat-trick on close; first gram when the jar total reaches one gram at the typed gold price; settled when every debt is cleared. Idempotent. Milestone events exposed for the UI (new level, stamp earned).
- Profile in `SettingsStore`: name Qersh uses, birthday, kind of work (salaried / freelance / student / runs the house), household size, in school, main goal, birthday target. Home card order by main goal (decision 7). Household scaling hooks. Delete all data (decision 6) in a `DataWipe` class.
- Tests per rule (week bounds, goals, league saved, hat-trick, levels, stamp idempotence, first gram, settled, profile order, wipe).

## Task 41: Buying data — wishes and the basket

- Migration 4→5: `wish` (id, name, price, parkedAt, answer WAITING/BOUGHT/CHANGED_MIND, answeredAt, installmentMonthly?, installmentMonths?), `tracked_item` (id, name, normalisedName, unit?, addedByUser, createdAt). `entry.trackedItemId` / `entry.unitPrice` already exist — use them. Migration test.
- `domain/BeforeBuy.kt`: tickets = price ÷ today's ticket to one decimal, spoken form ("7 ونص", "8", "7.3"); work days = price ÷ (average monthly income ÷ 30), hidden when income unknown; gold = price ÷ gold price, hidden without one; season share = price ÷ saved for the nearest season, hidden when 0; installment total, extra, ticket effect per day and for how many months; "تاخد N أيام من تذاكرك" for cash.
- `WishRepository`: park (48 h), list with hours left, due-for-answer list, "صرفت نظر" (optionally move the price to the jar, capped at spendable), "اشتريتها" (returns a prefill for the add sheet), total changed-my-mind amount.
- `BasketRepository` (Plus): auto-tracking (decision 3), item last price, change vs first price in the last 3 months, last place, cheapest place, times per month; basket change (usual monthly quantities × today's prices vs 3 months ago); suggested ticket = current × (1 + change), offered only; habit facts (most expensive weekday with its average vs the rest, entries after 21:00 with count and sum, cash vs wallet spend difference). Never invents prices.
- Add sheet: optional item + unit price row for SPENT (decision 3), saved on the entry.
- Tests per rule.

## Task 42: Match and album screens

- Match per `Match.dc.html`: pitch scoreboard (إنت vs الأسبوع اللي فات, week number, "فاضل N يوم على الصفارة"), the rule line, match events day by day, the hat-trick card, the league table and the summary line, "كمّل الماتش" (back to Home). First week → "أول ماتش ليك" + "مفيش خصم الأسبوع ده. العب براحتك.". Goal motion per `05-MOTION` §5 (score digit flips, event row slides in) when a new goal appears since last visit; haptics per `06-HAPTICS`. Weeks beyond 4 behind `PlusLocked`.
- Album per `Album.dc.html`: Qersh with level and progress, the three streak tiles, the proverb grid (collected and locked; tapping a collected card opens it), stamps, recent stubs, links to basket, pockets and (later) settings. Empty → "الألبوم مستني أول مثل" with "روح للتذكرة". Milestone haptic and a short celebration when a new level or stamp is seen for the first time.
- Home wiring: streak pill → album, "أسبوعك" → match.

## Task 43: Before you buy and the wishlist

- Per `BeforeBuy.dc.html`: "نِفسك في إيه؟" (name + price with the keypad), "دي تساوي" tiles (only the ones with data), "قسط ولا كاش؟" (Plus; the user types monthly and months), Qersh's "نام عليها يومين." card with "حطّها في «نِفسي فيه» 48 ساعة" (Confirm haptic; the card shrinks into the list), the "نِفسي فيه" list with hours left, answered state "صرفت نظر · X للحصّالة" / "اشتريتها", the total changed-my-mind line. Empty list → "مفيش حاجة نِفسك فيها" + "اكتب حاجة". Home's 48-hour card (decision 2). Plan tab link (decision 1).

## Task 44: Basket and profile

- Basket per `Basket.dc.html` (Plus-locked per the Phase 6 pattern): the change hero ("+9%", "غليت عليك", then and now), tracked items with last price, context line and change, "تابع سعر حاجة تانية" (add a tracked item by name), the suggestion card ("زوّد التذكرة" stores a raised daily amount the same way the ticket is changed elsewhere, "سيبها" dismisses for the month), three habit facts. Empty → "سجّل نفس الحاجة مرتين وأنا أبدأ أتابع سعرها."
- Profile per `Profile.dc.html`: the yellow identity card (name, level, three stats, share button — no amounts), name, birthday with "بعد N يوم", kind of work chips, the "عيدية منك ليك" card, household steppers with their lines, main goal choices with the note, the privacy block and "امسح كل بياناتي من الموبايل" (decision 6).
