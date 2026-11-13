# Data and rules

What to store and how every number is calculated, in plain words. No code. Money is always stored as whole
piastres (minor units) and never as a decimal. One primary currency (EGP by default); amounts in other
currencies are never added to it.

## What to store

| Thing | What it holds |
|---|---|
| Settings | Language, payday day (1–31, last day, or "not fixed"), reminder hour, lock, hide amounts, rounding step, gold price per gram and the date it was typed, haptics on/off, Plus state |
| Pocket | Name, kind (cash, mobile wallet, bank, under the tile), currency, opening balance, colour, archived; "counts in the ticket" (false for under the tile) |
| Entry | Kind (spent, received, moved), amount, pocket, destination pocket for moves, category, note, when it happened, when it was logged, round-up amount, recurring link, optional tracked item and unit price |
| Category | Name, icon, colour, parent |
| Day ticket | Date, ticket amount, carried-in amount from yesterday, spent, entry count, status (open, closed by hand, closed automatically), leftover, where the leftover went, proverb card earned |
| Envelope | Name, colour, monthly amount, kind (spending or fixed bill), linked categories |
| Recurring item | Name, amount, pocket, schedule, next due date, envelope, paid this cycle |
| Jar movement | Amount, source (ticket leftover, round-up, manual, changed-my-mind), goal it is earmarked for, date |
| Goal | Name, target, optional date |
| Season | Kind (Ramadan, Eid al-Fitr, Eid al-Adha, summer, school, personal), date or Hijri date, target, checklist items with amounts |
| Person | Name, optional phone, kind (person, shop) |
| Debt | Person, direction (they owe me / I owe), amount, reason, date, due date, payments |
| Tab item | Shop, what, amount, date, settled |
| Occasion and gift | Occasion (name, date, whose), gift rows: person, amount, direction (received / given) |
| Gameya | Name, monthly amount, members in turn order, start month, my turn, payments per member per month, payout plan |
| Wish | Name, price, when parked, answer (waiting, bought, changed my mind), installment details |
| Tracked item | Name, unit, price history (date, price, place) |
| Proverb card | Number, text, meaning, rare or not, date collected |
| Stamp | Kind, date earned |

Everything is local. A backup is one encrypted file. Schema changes always migrate existing data; never wipe.

## The daily ticket

**Each morning (first open after midnight, or at midnight if the app is running):**

1. **Spendable** = the total of all pockets that count in the ticket, in the primary currency.
2. **Reserved** = recurring items and fixed-bill envelopes still unpaid and due before the next payday, plus
   money earmarked in the jar.
3. **Days left** = the number of days from today up to and including the day before the next payday (at least 1).
   For "not fixed" income use a rolling 30 days from the last income entry.
4. **Ticket** = (Spendable − Reserved) ÷ Days left, rounded **down** to a whole pound. Then add anything carried
   in from yesterday ("تذكرة بكره").
5. Store it for the day. It does not change during the day except when income is logged (then tomorrow's is
   recalculated, and today's stays).

**During the day:** spent = the sum of today's "spent" entries from pockets that count, excluding fixed bills
paid from their envelope. Left = ticket − spent, shown as 0 if negative. Holes = number of entries (show 8, then
"+N").

**Over the ticket:** nothing special is stored. Tomorrow's calculation sees a smaller Spendable and spreads the
difference across the remaining days. The copy states the per-day effect: overspend ÷ days left.

**If Spendable − Reserved is zero or negative:** do not print a zero ticket. Enter end-of-month mode.

## Closing the day

- By hand, any time: leftover = ticket − spent, if positive.
  - **Jar** (default): a jar movement for the leftover.
  - **Tomorrow's ticket:** stored as carried-in for tomorrow only.
  - **Emergency envelope:** added to that envelope.
- Closing by hand earns one proverb card (the next uncollected, in a shuffled order fixed per user) and extends
  the streak.
- Not closed by the first open of the next day: closed automatically, leftover to the default destination, no
  card, streak ends (unless the weekly safety ticket is available, Plus).
- Entries added later to a closed day adjust that day's spent and the jar movement it produced.

## Streak, levels, stamps

- **Streak:** consecutive days closed by hand. A day with no entries can still be closed (a no-spend day).
- **Level** by tickets closed by hand: 1 مبتدئ (0), 2 ماشي (7), 3 منظّم (20), 4 مدبّر (40), 5 مقتصد (60),
  6 أسطى (100), 7 معلّم (180), 8 حوت (365).
- **Stamps:** first ticket; full week (7 days closed in one Saturday–Friday week); hat-trick (3 days in a row
  under the ticket); tight fist (2 no-spend days in end-of-month mode); first gram (jar reaches one gram of
  gold at the typed price); settled (cleared every debt).

## Round-ups (الفكة)

When on, each "spent" entry is rounded up to the next 5, 10 or 50 (the user's step). The difference is recorded
as a jar movement from the same pocket and counts as spent from today's ticket. An entry already on a round
figure adds nothing. Turning it off stops new round-ups and keeps old ones.

## The jar and gold

- Jar total = the sum of jar movements minus withdrawals. It is a share of the user's own money, not a pocket.
- Gold grams = jar total ÷ the price per gram of 21-karat gold **typed by the user**, shown to two decimals,
  with "السعر اللي كتبته" and the date. The app never states a market price as fact. If no price was typed,
  the gold view asks for one.
- "فاضل على أول جرام" = price per gram − jar total, when the jar is under one gram.

## Seasons

- Islamic occasions are stored as Hijri dates and shown as "تقريبًا" (the real day depends on moon sighting).
  Summer and school are Gregorian months the user can change.
- Per day needed = (target − saved) ÷ days until the season, rounded up.
- "All seasons together": the sum of each season's monthly need, compared with the user's average monthly jar
  growth over the last 3 months. If it is more, say so and offer to rank them. Never hide the gap.

## Envelopes

- Left = monthly amount − spending this pay cycle in the envelope's categories.
- "هيخلص يوم N": left ÷ average daily spending in that envelope this cycle, counted forward from today. Shown
  only when that day is before payday and there are at least 5 days of data.
- Fixed-bill envelopes are "مدفوع" once their recurring item is paid this cycle.
- On income: split by the saved split (percentages or fixed amounts). Anything not assigned is shown as
  "من غير ظرف".

## End-of-month mode

- Offered when the ticket falls below 60% of the user's average ticket for the cycle, or when Spendable −
  Reserved is not positive. Can be switched on by hand.
- Shows days left and Spendable − Reserved, divided evenly into small tickets.
- "Biggest optional habits": the two non-essential categories with the highest spend this cycle (essentials are
  rent, bills, groceries, transport, health, education). Pausing a habit only adds a reminder when it is logged.
- No-spend challenge: each no-spend day adds (that day's ticket ÷ remaining days) to every remaining ticket.

## Weekly match

- A week runs Saturday to Friday.
- **Goal for:** a day closed with leftover ≥ 0. **Goal against:** a day closed over the ticket. A day not
  closed scores nothing.
- Result = this week's goals for versus last week's goals for. The league lists each week with its seven dots
  and the amount saved (sum of leftovers minus overspends, not below zero).
- Hat-trick: three consecutive goals for.

## Before you buy

- Tickets = price ÷ today's ticket (one decimal, spoken as "7 ونص").
- Work days = price ÷ (average monthly income ÷ 30). Hidden if income is unknown.
- Gold = price ÷ typed gold price. Share of season = price ÷ what is saved for the nearest season.
- Installments: total = monthly payment × months. Extra = total − cash price. Ticket effect = monthly payment ÷
  30 per day, for that many months. The user types the payment and months exactly as the shop said them; the
  app does not assume an interest rate.
- Parked for 48 hours. Afterwards: "صرفت نظر" asks whether to move the price into the jar (only up to what is
  spendable); "اشتريتها" opens the add sheet prefilled.

## People

- Balance per person = what they owe me − what I owe them, per currency.
- The tab is a list of unsettled tab items per shop; settling logs one "spent" entry for the total.
- Duty ledger suggestion for an occasion = the amount that family gave the user, shown as "X أو أكتر".
- Gameya: payout = monthly amount × members. My turn month = start month + (turn − 1). Paid so far = monthly
  amount × months elapsed in which I paid.

## Basket

- An entry can name a tracked item and a unit price. An item is tracked automatically after it is logged twice
  with a unit price, or when the user adds it.
- Item change = latest price versus the first price in the last 3 months.
- Basket change = total cost of the user's usual monthly quantities at today's prices versus 3 months ago.
- Suggested ticket = current ticket × (1 + basket change), offered, never applied silently.

## SMS

Reading is on the device only. Pasted or (Plus, with permission) incoming messages from short codes or named
senders are parsed for amount, direction, source and remaining balance. Messages from ordinary mobile numbers
are ignored. Nothing is saved until the user confirms the prefilled entry. The message text is never stored.

## Subscriptions

- A subscription is a recurring item with extra fields: trial end date, yearly price (optional), who it is
  shared with and each share, and the last three monthly answers to "still using it?".
- Monthly total = the sum of monthly prices (yearly ones ÷ 12). Tickets = total ÷ today's ticket, rounded down.
- Shared: the user pays the full price; each friend's share becomes "owes me" in People on every renewal.
- Question of the month: pick the subscription not asked about longest. Two "not really" answers in a row
  unlock the saving line: monthly price × 12.
- Trial: remind the day before the end date. If the user says "I'll keep it", it becomes a normal subscription.

## One pocket

- Moved this month = the sum of everything leaving and entering the pocket in the calendar month, compared with
  a limit **typed by the user**. The warning appears when moved + reserved payments due from this pocket would
  pass the limit.
- Fees = entries the user marked as a fee (the add sheet has an optional "رسوم" amount on transfers and cash
  outs). The app does not assume any provider's fee.
- "Matches the last message" = the balance parsed from the latest SMS equals the pocket's balance.

## Profile

- All fields optional. Stored on the phone only, included in the encrypted backup.
- Main goal reorders Home's cards below the ticket: last the month → pay cycle card first; save → jar first;
  pay off → people first; occasion → seasons first.
- Household numbers scale suggestions only (never the user's own amounts): school season default target and
  the grocery envelope suggestion.
- Birthday plan: target ÷ days until the birthday = per day.
- "Delete all my data" asks once, names what will go, and offers to save a backup first.

## Payday ritual

- Triggered by an income entry of at least half the user's usual salary, or by the user from the roll.
- Order matters and is fixed: jar share, then debts the user ticks, then fixed costs due this cycle. What is
  left ÷ days in the cycle = the daily ticket (rounded down).
- The jar share is remembered for next month. Skipping any step is one tap; nothing is forced.

## Widgets

- Widgets read the stored day summary; they never calculate. A one-tap button logs the same repeat entry as on
  Home and updates the widget at once.
- With the lock on, every amount in widgets and notifications is replaced by dots.

## Freelancer mode

- Store: the vault (a special pocket that never counts in the ticket), self-salary amount, self-payday (day of
  the month), and client jobs (client, label, amount, due date, paid or not).
- Work income always goes to the vault. On self-payday the self-salary moves from the vault to spending money
  and runs the payday ritual exactly like a normal salary. The daily ticket comes from that ritual.
- Buffer months = vault ÷ self-salary, shown rounded down to the nearest half ("شهرين ونص"). "Safe until" = today
  plus that many self-paydays.
- If the vault holds less than the self-salary on payday: pay what is there, say so plainly, and build the ticket
  on the amount actually paid. Never go negative.
- Raise suggestion: only when the six-month average income is at least 10% above the salary **and** the buffer
  is two months or more. Suggested amount = the average rounded down to the nearest 500. Shown at most once a
  month; "خلّيه زي ما هو" silences it for three months.
- Lower suggestion: when the buffer falls under one month. Same limits. Nothing ever changes without a tap.
- A client job is overdue the day after its due date. "فكّره بلُطف" only opens the share sheet with a ready
  message; the app sends nothing itself. Marking a job paid creates the income entry in the vault.

## Installments

- Store per plan: name, monthly amount, day of the month, total count, paid count, optional pocket it is paid
  from.
- Monthly total = sum of active plans. Per day = monthly total ÷ 30, rounded to the nearest pound. Remaining =
  sum of (count left × monthly amount). A plan's end month comes from its count left; the freedom date is the
  latest end month.
- Installments are fixed costs: they are reserved in the payday ritual, so they reduce the ticket and never
  punch a day.
- On its day each month a plan creates its entry and adds one to the paid count. The user can undo it.
- Ticket growth when a plan ends = its monthly amount ÷ 30, rounded down.
- Early-payoff suggestion: only for the plan with the least remaining, only when jar money not tied to a goal
  covers it. The payoff amount is typed or confirmed by the user. Shown at most once a month per plan.

## Monthly story

- Built once when the cycle closes, from stored day summaries; saved so replaying never recalculates.
- Counts: days torn under the ticket, days torn over it, days never torn. Compare with the previous cycle and
  with the user's best cycle. "Best month" is said only when it is true.
- Top three envelopes by spend, the single largest entry, total moved to the jar, cards won, longest streak.
- A month with fewer than 7 torn days gets a short two-slide story with no comparisons and no blame.
- Sharing renders a picture on the phone. With "without amounts" on, every amount is drawn as dots; counts,
  streaks and the month name stay.

## Kids' allowance

- Store per child: name, birth year, colour, weekly allowance, optional jar goal (label, target, saved), match
  rule (per X saved, parent adds Y). Shared: handover day, chores (label, bonus, for which child or any).
- Handover amount for a child = weekly allowance + ticked chore bonuses + match earned this week − approved
  advances. The button shows the sum for all children. One tap creates one entry from the kids' envelope.
- An advance cannot be more than half of next week's allowance, and only one can be open per child.
- "Left until handover" = what was handed over minus what the child spent (logged by the parent or on the child
  screen). It never goes below zero; extra spending is the parent's own entry.
- Match is counted on money moved into the child's jar during the week, in whole steps of X only.
- The child screen is the app pinned to one child's view. Leaving it requires the parent's PIN or fingerprint
  even when the app lock is otherwise off (the app asks the parent to set a PIN the first time).
- Free plan: one child. Children beyond the first are kept but read-only if Plus ends.

## Lock

- PIN is 4 to 6 digits, stored only as a salted hash in the device's secure storage. Fingerprint uses the
  system prompt; the app never sees biometric data.
- Locks on cold start and after 60 seconds in the background. While locked the app is hidden in recent apps.
- Wrong tries: 5 wrong = wait 30 seconds, then the wait doubles for every further wrong try, up to 15 minutes.
- Forgotten PIN: the only way in is to erase this phone's data and restore a backup with the backup password.
  The app explains this in one sentence before doing anything.

## Backup

- One file holding everything, encrypted with a key derived from the user's backup password (AES-256-GCM, key
  stretched with a slow function). The password is never stored in plain form and never leaves the phone.
- Saved through the system file picker, so "the user's Drive" needs no sign-in inside the app and no network
  permission beyond what the system picker does.
- Automatic backup: weekly, at night, while charging. Keep the last 4 files; delete older ones.
- "Last backup" shows the real time of the last successful write. A failed automatic backup shows a quiet line
  on the backup screen and in settings ("آخر نسخة فشلت. جرّب تاني.") and never a full-screen alert.
- Export to Excel: a CSV in UTF-8 with a byte-order mark so Arabic opens correctly. Not encrypted; say so.

## Safety rules

- Deleting is soft for 5 seconds (undo), then real.
- Restoring a backup replaces data only after an explicit confirmation naming the backup's date.
- Amount inputs are capped at a sane length; notes at 120 characters.
- With the lock on, widgets and notifications show dots instead of amounts.
