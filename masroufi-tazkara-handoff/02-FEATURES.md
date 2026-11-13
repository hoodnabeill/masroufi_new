# Features

Each feature lists what it does, the rule behind it, and whether it is Free or Plus. Exact calculations are in
`11-DATA-AND-RULES.md`. Screens are in `04-SCREENS.md`.

## The daily loop (all Free)

### 1. Today's ticket (تذكرة اليوم)
- Shows what the user can spend today, the amount spent so far, and one punched hole per entry.
- Printed fresh on the first open after midnight and fixed for the day (it does not shrink because a bill is due
  tomorrow; bills are reserved separately).
- Tapping the ticket opens the wallets screen.

### 2. Punch an expense (سجّل / اخرم)
- Bottom sheet: type (spent / got / moved), amount, category, wallet, optional note.
- "بتتكرر معاك" chips on Home log a repeated spend in one tap (same category, amount and wallet as last time).
- Type or speak: "20 فول وطعمية" is parsed into amount + category.
- Optional round-up line: "قرّبها لـ 30 و 5 جنيه فكة للحصّالة" (Plus, see 12).

### 3. Close the day (قفّل اليوم / اقطع التذكرة)
- The user tears the ticket. They choose where the leftover goes: the jar (default), tomorrow's ticket, or the
  emergency envelope.
- If the day ended over the ticket, there is nothing to distribute: the overspend is spread over the remaining
  days automatically, and the copy says so calmly.
- A day not closed by the next morning closes itself: leftover goes to the default destination, no proverb card,
  and the streak breaks (unless a safety ticket is used, Plus).

### 4. Proverb reward (مثل اليوم)
- Each manual close flips one collectible proverb card (60 in the set, plus rare cards for milestones).
- Shows what the close earned: amount to the jar, streak length, days closed this week.
- The card can be shared as an image.

### 5. Streak (السلسلة)
- Counts consecutive days closed by hand. Shown as a flame pill on Home and a week strip of mini stubs.

### 6. End-of-month mode (وضع آخر الشهر): Free forever
- Offered automatically when money per remaining day drops below a comfortable level, or switched on by hand.
- Splits what is left into small daily tickets, reserves the bills due before payday, lets the user pause their
  two biggest optional habits ("وقّفها لحد المرتب", a reminder only, never a block), and offers the no-spend-day
  challenge.
- Shows where emergency money could come from: the jar, people who owe the user, the emergency envelope.

## The money

### 7. The roll (الدفتر): Free
- History as a roll of day stubs: green when the day closed under the ticket, red when over, yellow for today,
  grey for non-ticket days (salary, rent).
- Today is expanded with its entries. Search, month switcher, filters (all, spent, income, recurring).

### 8. Pockets (الجيوب): Free
- Wallets: cash, mobile wallet, bank account, and "تحت البلاطة" (money kept at home, excluded from the ticket).
- Cash counter: enter how many 200s, 100s, 50s and small change you hold; the app reconciles the balance.
- Quick actions: move between pockets, ATM withdrawal, wallet top-up.
- Paste a bank or wallet SMS and it is read on the phone into a ready entry (auto-reading of incoming SMS is Plus).

### 9. Envelopes (الظروف): Free up to 4, unlimited in Plus
- Budgets drawn as paper envelopes: what is left, a bar, and one honest line ("هيخلص يوم 16 بالمعدل ده").
- Fixed bills live in sealed envelopes ("مدفوع"). Move money between envelopes with one drag.
- When salary lands it is split across envelopes by the user's saved split.

### 10. Recurring and installments: Free
- Rent, internet, gameya, installments. Due items are reserved out of the ticket ahead of time.

## Saving

### 11. The jar (الحصّالة): Free
- Where leftovers land. A virtual share of the user's own money, not a separate account (the screen says so).
- Sources: ticket leftovers, round-ups, manual deposits. Goals inside the jar (e.g. a new phone).

### 12. Gold view and round-ups (بالدهب / حوّش الفكة): Plus
- The jar shown in grams of 21-karat gold at a price the user types in ("عدّل السعر").
- Round-ups: every entry rounds up to the nearest 5, 10 or 50 and the difference goes to the jar.

### 13. Seasons (المواسم): Plus
- Plan ahead for Ramadan, Eid al-Fitr, Eid al-Adha, summer holiday, back to school, and personal occasions
  (weddings). Each shows a countdown, a target, what is saved and the amount per day that gets there.
- A combined view says honestly when the seasons together need more than the user usually saves, and offers to
  rank them.
- Zakat calculator (zakat al-mal and zakat al-fitr) from the user's own numbers; the user confirms the nisab.

## People

### 14. Loans and split bills (سلف / قسمة): Free
- Who owes the user, who the user owes, with a gentle reminder message through the phone's own apps.
- Quick split: paid for the group, pick who shares, each share is recorded.

### 15. The tab (النوتة): Free
- A running tab at a kiosk or grocer. Add items as you take them, settle in one tap.

### 16. The duty ledger (دفتر الواجب / النقوط): Plus
- Who gave the user how much at their wedding or occasion, and what the user gave others.
- Before an occasion it reminds the user what that family gave them, so the gift returned matches.

### 17. Gameya (الجمعية): Free for one, unlimited in Plus
- Members in a circle with whose turn it is, who has paid this month, a plan for the user's own payout, turn
  swapping, and sharing the statement to the group chat. Records only; money moves between people as usual.

## Rewards and insight

### 18. Weekly match (ماتش الأسبوع): Free (history beyond 4 weeks is Plus)
- The week is a football match: this week against last week. A day closed under the ticket is a goal for the
  user; a day over is a goal against. Three in a row is a hat-trick stamp and a rare proverb card.
- "دوري الأسابيع": the user's own weeks as a league table.

### 19. Before you buy (قبل ما تشتري): Free (installment comparison is Plus)
- Type something you want and its price. The app turns it into things that mean something: how many daily
  tickets, how many days of work, how many grams of gold, what share of the nearest season.
- "قسط ولا كاش؟": compares paying cash with an installment plan and shows the real extra cost and how much the
  daily ticket shrinks.
- "نِفسي فيه": park the item for 48 hours. After that the app asks once: still want it? "صرفت نظر" can move the
  amount to the jar.

### 20. Your basket (سلّتك): Plus
- Tracks the prices of things the user buys again and again, from their own entries only, and shows how much the
  basket rose over three months.
- Suggests raising the daily ticket to keep the same standard, or keeping it and tightening.
- Plain habit facts: most expensive weekday, late-night orders, cash versus wallet.

### 21. The album (ألبومك): Free
- Level, streaks, collected proverb cards, stamps (first ticket, full week, hat-trick, tight fist) and old stubs.

### 22. Safety ticket (تذكرة الأمان): Plus
- One missed day per week does not break the streak.

## Trust and settings (all Free)

- App lock with PIN and fingerprint, hide amounts, hide from recent apps.
- Encrypted backup file, restore, optional sync to the user's own storage.
- Arabic and English, daily reminder time, payday setting.

## Added in round 3 (all Free)

### 23. Subscriptions (الاشتراكات)
- Everything that renews by itself: streaming, internet, gym, mobile bundle, cloud storage. One total per month,
  shown also as "how many daily tickets" and per year.
- **Free-trial watch:** a trial with an end date shows a countdown, says what it will cost if not cancelled, and
  offers a reminder the day before.
- **Question of the month (سؤال الشهر):** once a month Qersh asks about one subscription, "لسه بتستخدمه؟" with
  three answers (yes / not really / I stopped it). Two "not really" in a row and he shows what stopping it saves
  per year. He asks; he never cancels anything.
- **Shared subscriptions:** a subscription split with friends shows the user's share and what the others owe,
  linked to the People tab.
- **Yearly hint:** when the user typed both a monthly and a yearly price, show the saving.
- One-tap adding from a short list of common names. Names are plain text labels; prices are always typed by the
  user.

### 24. One pocket (جيب واحد)
- A pocket's own screen: balance, whether it matches the last bank or wallet message, four actions (top-up,
  transfer, cash out, reconcile), and its recent entries.
- **Monthly wallet limit:** the user types the limit their provider gave them; the screen shows how much moved
  this month and warns before a planned payment would not fit. The app never states a provider's limit itself.
- **Fees eaten (الرسوم أكلت كام؟):** the fees the user logged this month, by kind, with one practical tip.
- Switches: "counts in today's ticket" and a low-balance alert.

### 25. Profile (ملفك)
- What Qersh calls the user, birthday, kind of work, how many people they support and how many are in school,
  and the one thing that matters most now (last the month / save / pay off / prepare for an occasion).
- These tune the app: Home's order follows the main goal; the school season and envelope suggestions follow
  the household numbers; the birthday gets a "gift from you to you" saving plan.
- **Shareable card:** level, streak, proverbs, matches won. Never any amounts.
- Everything is optional and stays on the phone. A plain "delete all my data from this phone" action.

### 26. Payday ritual (يوم القبض)
- When income of salary size lands, a three-step screen instead of a silent entry:
  1. **Pay yourself first:** a share (5 / 10 / 15% or an amount) goes to the jar before anything else.
  2. **Settle what you owe:** the user's own debts and tabs, ticked to pay now.
  3. **Reserve the fixed costs:** rent, gameya, bills.
- One bar shows where the whole salary went, and what is left becomes the month's daily ticket. The button
  "اطبع تذاكر الشهر" prints the month.

### 27. Widgets and the evening notification
- Small ticket widget (today's number and holes), small match widget, and a wide widget with the ticket plus
  three one-tap repeat buttons and "+".
- With the app lock on, widgets show dots instead of amounts.
- The evening reminder can close the day from the notification itself ("اقطعها للحصّالة" / "بعد ساعة").

## Added in round 4

### 28. Freelancer mode (مرتب لنفسك): Free
- For people whose income comes in lumps. Every payment for work lands in **the vault (الخزنة)**, not in
  spending money. Once a month the user pays themself a fixed **self-salary** from the vault, and the daily
  ticket is built on that salary, so a great month and an empty month feel the same day to day.
- The vault shows how many months of salary it holds ("تكفّي مرتبك شهرين ونص") and the date the salary is safe
  until if no work comes at all.
- Six months of income against the salary line, so the user sees the lumps and the steady line together.
- **Raise or lower, only as a suggestion:** when average income and the vault allow it, Qersh suggests a higher
  salary; when the vault is thin he suggests a lower one. The user decides; nothing changes by itself.
- **Money still with clients:** job, client, amount, due date. Overdue ones offer "فكّره بلُطف", which opens the
  share sheet with a polite ready message. When it is paid it goes into the vault with one tap.
- Turned on from Profile (kind of work) or onboarding's payday step ("مفيش يوم قبض ثابت").

### 29. Installments (أقساطي): Free
- Every installment plan in one place: monthly amount, day of the month, how many are left, the month it ends.
- One total per month, shown also as "how much it takes from each daily ticket", and everything still owed.
- **Freedom date (يوم الحرية):** the month the last installment ends, with a timeline of when each plan ends and
  how much the daily ticket grows each time.
- **Finish one early:** when the smallest plan's remaining amount can be covered by free jar money, Qersh shows
  what paying it off now adds to the ticket. The amount is what the user confirms with the seller; the app never
  assumes a discount or a fee.
- A new purchase on installments goes through "قبل ما تشتري" first (the comparison is Plus, as before).

### 30. Monthly story (حكاية الشهر): Free
- On the first open after the month closes: five full-screen slides about the month, in the ticket's yellow.
  Tickets torn, where the money went, the biggest single hole, what reached the jar, cards collected, the
  longest streak, and one suggestion for next month.
- Compares only with the user's own earlier months. Never with other people.
- **Share without amounts** is on by default: the shared picture keeps counts and streaks and replaces every
  amount with dots. Each slide can be shared alone.
- Kept in the album; any past month can be replayed.

### 31. Kids' allowance (مصروف العيال): Free for one child, unlimited in Plus
- Each child has a weekly allowance, a handover day and their own small ticket in their own colour, with what is
  left until the next handover.
- **Advance (سلفة):** a child can ask for part of next week early. The parent answers with one tap: "ماشي،
  تتخصم" (it comes off next week) or "يستنى السبت".
- **Chores that add to the allowance:** the parent's own list (label and bonus). Ticking one adds the bonus to
  the next handover.
- **A jar for the child, with a match:** the child saves toward one goal and the parent can match it ("كل 10
  تحوّشهم، إنت بتزوّدلها 5"). The best money lesson in the app.
- **Child screen (شاشة الطفل):** hand the phone over and the child sees only their ticket and jar. Getting out
  needs the parent's PIN or fingerprint. No child account; nothing leaves the phone.
- Handover is one button for all children and one entry from the kids' envelope.

### 32. Settings (الإعدادات): Free
- Payday, tear-off reminder time, where the leftover goes (jar by default), round-ups, language, vibration,
  ticket sounds (off by default), hide amounts, lock, backup, reading bank messages (Plus), legal, feedback.
- Every row shows its current value. Switches act at once, with no save button.

### 33. Lock and backup (القفل والنسخة الاحتياطية): Free
- **Lock:** a 4 to 6 digit PIN and fingerprint. Locks on opening and after a minute in the background. Five
  wrong tries start a waiting time. There is no "reset PIN by email" because there is no account: a forgotten
  PIN means restoring from a backup.
- **Backup:** one encrypted file with a password the user chooses, saved where the user chooses (their phone or
  their own cloud folder). Optional weekly automatic backup. The app says plainly that a forgotten backup
  password cannot be recovered by anyone.
- **Restore** names the backup's date and what it replaces, then asks for the password.
- **Export to Excel:** a plain file of every entry. It is not encrypted and the app says so before saving.
- **New phone:** three steps on the backup screen.

## Free vs Plus at a glance

| | Free | Plus |
|---|---|---|
| Ticket, punching, closing, proverbs, streak | Yes | Yes |
| End-of-month mode | Yes | Yes |
| Roll, pockets, cash counter, SMS paste | Yes | Yes |
| Envelopes | 4 | Unlimited |
| Gameya | 1 | Unlimited |
| Loans, tab, split | Yes | Yes |
| Jar and goals | Yes | Yes |
| Gold view, round-ups | | Yes |
| Seasons and zakat | | Yes |
| Duty ledger | | Yes |
| Basket and habit facts | | Yes |
| Installment comparison | | Yes |
| Auto-read incoming SMS | | Yes |
| Safety ticket for the streak | | Yes |
| Match history | 4 weeks | All |
| Backup, lock, languages | Yes | Yes |
| Freelancer mode and client money | Yes | Yes |
| Installments and freedom date | Yes | Yes |
| Monthly story | Yes | Yes |
| Kids' allowance | 1 child | Unlimited |
