# Masroufi Plus (مصروفي بلس)

## The rule for what is paid

Free must be a complete, honest product: a person on a tight month can run their whole money life on it forever.
Plus is for **planning ahead and growing**: things that matter once the basics are under control. Nothing that
protects a user in a bad month is ever paid.

| Always free | Plus |
|---|---|
| The daily ticket, punching, closing the day | Gold view of the jar and round-ups (الفكة) |
| Proverb cards, streak, album | Seasons (المواسم) and the zakat calculator |
| End-of-month mode | The duty ledger (دفتر الواجب / النقوط) |
| The roll, pockets, cash counter, SMS paste | Your basket (سلّتك) and habit facts |
| 4 envelopes, 1 gameya | Unlimited envelopes and gameyas |
| Loans, the tab, quick split | Installment comparison in "قبل ما تشتري" |
| The jar and goals | Auto-reading of incoming bank and wallet SMS |
| Weekly match (last 4 weeks) | Full match history |
| Lock, backup, restore, languages | Safety ticket: one missed day a week keeps the streak |

## Trial

- 14 days of Plus, starting on day 7 of use (not at install: the user must first feel the free product).
- No card or payment method to start it. It starts with one tap on an honest card: what is in it and the date
  it ends.
- Three days before the end, one quiet line on Home. The paywall appears the day before it ends, and once more
  on the day. After that it is only reachable from Settings or by tapping a Plus feature.
- When the trial ends the Plus features stop; **nothing the user entered is deleted or hidden from export**.
  Seasons, ledger rows and basket items stay stored and reappear the moment Plus is on again.

## Prices (proposal, to be decided and tested)

| Plan | Price | Shown as |
|---|---|---|
| Yearly, preselected | 399 EGP | "يعني 33 جنيه في الشهر" and a "وفّر 32%" tag |
| Monthly | 49 EGP | "تتجدد كل شهر" |

Only these two prices are ever on screen. No third "lifetime" or "family" tier, no decoy. Billing is through
Google Play; the paywall says so and says it can be cancelled there at any time. Prices shown must be the ones
Play returns for the user's country, not hard-coded text.

## The paywall (`Paywall.dc.html`)

### What it must do
1. **Show what they are about to lose, in their own numbers.** Not a feature list. Each row is built from the
   user's real data during the trial:
   - "0.37 جم · حصّالتك بالدهب"
   - "86 ج · الفكة اللي بتتحوّش لوحدها" (what round-ups collected this month)
   - "5 مواسم · خطة رمضان والعيد والمدارس" (how many they planned, the nearest one and what is saved for it)
   - "38 اسم · دفتر الواجب والنقوط" (rows they entered, and the next occasion)
   - "13 يوم · تذكرة الأمان لسلسلتك"
2. **Two prices, yearly selected by default.**
3. **One button**, emotional and in the user's voice: "خلّي حاجتي معايا".
4. **One headline:** "بعد بكره، دول هيقفوا" with sad Qersh.

### Rows: which to show
- Show only rows where the user actually has something to lose (a value above zero). Order by what they used
  most. Maximum five. If they used nothing, do not fake it: show the plain version instead ("جرّبت المجاني
  أسبوعين. دي الحاجات اللي ممكن تزيد عليه") with three features and the same two prices.
- A user who never had a trial (tapped a Plus feature from free) sees that feature first, described by what it
  would do with their data ("حصّالتك 1,840. بالدهب تبقى 0.37 جرام.").

### Honesty rules (not negotiable)
- Everything on it is true. "هيقفوا" means the features stop; the line "بياناتك كلها هتفضل محفوظة ومش هتتمسح.
  اللي هيقف هو المزايا دي بس." is always visible above the prices.
- No countdown timers, no "offer ends in", no fake discounts, no "X people bought today".
- The way out is always there and always works: a close button at the top and "لا شكرًا، كمّل بالمجاني" under
  the button, both at least 44 dp tall. Closing never asks "are you sure?".
- Never shown during end-of-month mode, during onboarding, right after an overspent day, or more than once a
  day.
- No dark default: yearly is preselected because it is cheaper per month and the screen says exactly what each
  plan costs and how it renews. The total charged today is on the button's confirmation sheet from Play.

### Motion and haptics
- The loss rows arrive one after another, 60 ms apart, each with a dashed carnelian outline (they are "about to
  be torn off"). This is the screen's one authored moment.
- Selecting a plan: Tick. Tapping the button: Confirm. Purchase complete: Milestone, sad Qersh flips to
  delighted, the dashed outlines turn solid turquoise, and the screen closes itself after 900 ms to wherever
  the user came from.
- Purchase cancelled or failed: stay on the paywall, no nagging, the line "الدفع ماكملش ومتخصمش منك حاجة."

## Where Plus appears inside the app

- A Plus feature in free shows its screen with the user's data blurred behind one card: what it would show them
  (using their real numbers where possible) and a button "جرّب بلس". Never a lock icon alone.
- Settings has one row: "مصروفي بلس" with the plan, renewal date and "إدارة الاشتراك" (opens Play).
- Restoring a purchase is automatic on reinstall; a manual "استرجاع الاشتراك" link exists in Settings.

## Wording for stores and legal

The Play listing and the in-app text must state: the price and period, that it renews automatically, how to
cancel, and that the free version stays usable without paying.
