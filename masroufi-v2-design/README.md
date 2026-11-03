# Masroufi v2 — design handoff

Arabic-first (RTL, Egyptian dialect), dark-first, offline-first. Every screen in `screens/` is a 390-px-wide phone mockup in HTML with inline styles; open any file in a browser to see it (fonts load from Google Fonts in the mockup only — bundle them in the app). Interaction logic for each screen is in the `<script>` block at the bottom of its file.

Sample numbers in the mockups come from the owner's real data on 2 Nov 2026 (balance 4,527; spent 1,105 in November: mobile 580, food 320, transport 205; Spotify 48 on 23 Oct via Vodafone Cash). Recap, gameya, debts and goal progress use sample data.

---

## 1. Design system — "Midnight Ledger"

### Colours
| Token | Hex | Use |
|---|---|---|
| bg / ink | #0B0A10 | App background |
| surface | #15131D | Cards |
| raised | #1D1B28 | Inputs, chips, inner tiles |
| track | #272435 | Progress tracks, selected segment |
| line | #2A2737 | 1 px borders, dividers |
| text-1 | #F4F2FA | Primary text |
| text-2 | #B4AFC6 | Secondary text |
| text-3 | #8E89A3 | Tertiary text (≥ 4.5:1 on surface) |
| lime (money / positive / primary CTA) | #C8F169 | Safe-to-spend, income, primary buttons, FAB. Text on lime: #12160A |
| violet (brand / navigation / premium) | #A99BFF | Links, levels, AI, premium. Text on violet: #17132E |
| amber (streaks) | #FFB648 | Streak, challenges, "soon" states |
| coral (warning) | #FF8C73 | Over-pace, overdue, destructive |
| Category: blue #7DB4FF, mint #5EE3A1, pink #E7A6FF, yellow #FFD466, orange #FFA36B | | Category icons on ~20% tint tiles (e.g. coral tile #33201C, blue #1A2638, mint #15302A, pink #2E1F38, yellow #332A12, orange #33231A, lime #24301A, violet #262046, amber #33260F) |

Rules: expenses are shown in text-1 (not red — spending isn't shameful). Income/positive = lime. Glass/blur only on floating elements (nav, sheets). One hero per screen.

### Type
- Display & numbers: **Readex Pro** 600–700. Hero number 64–72 sp, letter-spacing −2.
- Body: **IBM Plex Sans Arabic** 400–600.
- Scale: H1 28, H2 18, title 15–17, body 15, caption 12.
- Western digits, tabular. Money: `1,105 ج.م` (currency smaller + text-2). No `.00`. No trailing dot after ج.م.

### Shape & spacing
Radii: hero 28, card 24, tile 18, icon tile 14, chips/buttons full pill. Spacing 4/8/12/16/20/28. Screen gutter 16. Touch targets ≥ 44 dp. Primary button height 56.

### Icons
Stroke icons, 1.8 stroke, round caps/joins, 24 viewBox (Lucide-style). Never emoji.

### Motion
- Daily number rolls to its new value after every log (~300 ms).
- Light haptic on keypad taps; success haptic + reward screen on save.
- Screen content enters with fade + 20 px rise (450–550 ms, ease-out).
- Confetti only on milestones (2, 7, 30-day streaks, badges).

---

## 2. Navigation
Bottom bar: **الرئيسية · الحركة · [+] · الخطة · المزيد**. The + is a raised 60 dp lime circle opening the quick-add sheet. Budgets and Goals are merged into **الخطة** with a segmented control (الميزانيات | الأهداف). Subscriptions are merged into **المتكررة**.

---

## 3. Core formulas
- **Safe-to-spend today** = current total balance ÷ days until next income. Next income comes from onboarding/recurring income (monthly on 1st, 25th, weekly, irregular → end of month). Fallback: days left in month.
- **Today's bar** = spent today ÷ safe-to-spend.
- **Pace forecast** = balance ÷ average daily non-bill spending → run-out date. Bills (recurring) are excluded from pace.
- **Health score (0–100)** = average of: logging consistency, spending vs safe pace, emergency fund progress, bills tracked. Show 3 actions with point gains.
- **Recap "saved"** = (safe-to-spend × days) − actual spend for the month.
- **Gameya instalments** are recorded as savings/transfer, not expense.
- **Auto-split**: income × %, must total 100%. Daily number preview = (needs + wants) ÷ 30. Compare savings bucket with goal's monthly plan.
- **Goal boost**: months left = remaining ÷ (monthly + extraPerDay × 30); show new finish date and weeks saved.

## 4. Gamification
Points: +10 per day logged (first log of the day), +5 gameya instalment paid, +20 finishing onboarding, badge rewards as listed. Levels: مبتدئ 0 → منظّم 100 → شاطر 300 → محترف 700 → ملك الفلوس 1,500. Streak = consecutive days with ≥ 1 log. One free streak freeze per week, used automatically. Badges (9): أول خطوة، بدأت صح (3-day)، أسبوع كامل (7)، يوم صفر (no-spend day)، طبّاخ البيت (3 days no eating out)، المخطط (3 budgets)، حارس الطوارئ (emergency goal)، ابن الجمعية، الشهر الأخضر (month under safe pace). Weekly challenge card. Reward logging, never spending; no guilt copy.

## 5. Screens (file → purpose)
| File | Screen | Key behaviour |
|---|---|---|
| Main | Home | Safe-to-spend hero + privacy eye, streak card w/ week dots (week starts Saturday), type-to-log ("50 قهوة") + mic, quick chips with last amount, insights carousel, month stacked bar, recent grouped by day |
| AddSheet | Quick add | Keypad, type switch, category chips, wallet/date/note, live % of today's number, over-limit warning, income preview of new daily number |
| Reward | After saving | Streak +1, +points, level bar, one positive fact, next badge progress |
| Activity | الحركة | Month switcher with totals + daily average, filter chips, today's empty-state CTA, groups with daily totals, recurring badge |
| Budgets | الخطة / budgets | Suggested 3-budget plan (toggle each), total vs balance, weekly challenge |
| NewBudget | New budget | Category grid, amount presets, live "= X per day", spent so far, rollover toggle, alert at 80% |
| Goals | الخطة / goals | Main goal ring, save/withdraw, boost chips with live finish date, other goals |
| NewGoal | New goal | Templates (المصيف، رمضان والعيد، موبايل جديد، الطوارئ، الجواز، عربية، المدارس، تاني), live preview, timeframe → monthly/daily, auto-save toggle, 6 colours |
| More | المزيد | Profile/level card → achievements, recap entry, backup warning until enabled, tools grid, app settings |
| Insights | Analysis | Health score + factors, actions, forecast chart (mirrored for RTL), "اسأل مصروفي" (premium; precomputed answers in mock) |
| Recap | Monthly story | 5 auto-advancing slides, tap to advance, share image with hide-numbers switch |
| Achievements | Achievements | Level path, streak calendar, freeze, badge grid with detail |
| Gameya | جمعيات | Payout month/amount, 10-turn timeline, mark instalment paid, members' status, link payout to a goal |
| Debts | ديون وسلف | Owed vs owe, filter, overdue flags, reminder sheet with 3 tones → WhatsApp intent, mark settled |
| Wallets | محافظ | Total + split, wallet list, transfer/reconcile/ATM, paste-SMS parser (local only; Play policy blocks reading SMS directly) |
| Recurring | متكررة | Monthly fixed total, 30-day timeline, per-item reminder, instalments, recurring income CTA, quick-add common subs |
| AutoSplit | تقسيم تلقائي | Income presets, split presets/±5% steppers, live effects, auto toggle |
| Onboarding | 8 steps | Welcome (coin-drop) → goals → balance keypad → next income → daily number reveal (count-up ring) → first log + streak burst → reminder time + notification preview + freeze → done (summary, +30 pts, badge) |
| Icon | App icon | See below |
| Audit, Tokens | Reference boards | Problems in v1 and the token sheet |

## 6. App icon — "الجيب" (the pocket)
viewBox 0 0 100 100:
- Background: #0B0A10 (adaptive background layer).
- Coin: circle cx 50 cy 38 r 17, #C8F169; inner ring r 11.5 stroke #9FCC45 2.
- Pocket: `M23 45H77V65Q77 71.5 71 74.5L54 82.5Q50 84.5 46 82.5L29 74.5Q23 71.5 23 65Z` #A99BFF; hem rect x23 y45 w54 h3.5 #9483F5; dashed stitch inset #7463E3.
- Sparkle (large sizes only).
- ≤ 32 px: simplified — coin r18 at cy 37 + pocket only.
- Monochrome themed icon: glyph #3A2E8C on #E6E0FF, pocket outlined in bg colour to separate from coin.
- Foreground must stay inside the 66 dp safe zone of the 108 dp canvas.
- Splash: same glyph on #0B0A10.
