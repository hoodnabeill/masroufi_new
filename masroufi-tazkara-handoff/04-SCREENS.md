# Screens

36 artboards in `screens/`. Each entry: the file, what the screen is for, what is on it from top to bottom, and
its states. Sizes, colours and spacing come from the mock's inline styles. Navigation: a floating tab bar with
اليوم · الدفتر · [+] · الخطة · الناس. Everything else is pushed from those four.

Every screen has four states to build, not one: **loading** (skeleton in the shape of the content), **empty**
(Qersh, a human line, one action), **content**, and **error** (what happened and what to do). See
`07-COPY.md` for the words and `10-PERFORMANCE.md` for skeleton rules.

---

## The daily loop

### `Main.dc.html` — اليوم (Home, tab 1)
1. Header: date, greeting by time of day, streak pill (opens the album).
2. **The ticket**: label "تذكرة اليوم · صالحة ليوم واحد", "معاك النهارده", the big number, "من 240 · صرفت 84 في
   3 مرات", the row of holes (8 shown; more than 8 entries shows "+N"), caption. Qersh peeks over the top corner.
   Perforation. Stub: "اقطعها آخر اليوم" and the "قفّل اليوم" button.
3. Quick log field with a microphone button.
4. "بتتكرر معاك" chips: up to 3 repeated spends, one tap each.
5. "أسبوعك": seven mini stubs, Saturday first.
6. Two cards: the jar (amount and gold grams) and the nearest season (countdown and progress).
7. "13 يوم لحد المرتب": progress through the pay cycle and a one-line projection; link to end-of-month mode.
8. Locked proverb teaser: "قفّل يومك وافتح كارت جديد".

States: before the first entry of the day the holes are all dashed and the sub-line reads "لسه مصرفتش حاجة
النهارده". Over the ticket: the number shows 0, the ticket keeps its colour, and the sub-line reads "عدّيت تذكرة
النهارده بـ 35. اتوزّعوا على باقي الأيام." After closing: the ticket is replaced by tomorrow's preview ("تذكرة
بكره: 240") and the proverb card earned today.

### `AddPunch.dc.html` — سجّل (bottom sheet)
Type switch (صرفت / جالي / حوّلت), big amount, "هيفضل في تذكرة النهارده 131" updating live, category chips (last
used first), wallet and note, round-up line, keypad, the single button "اخرم التذكرة".
States: amount empty = button disabled and reads "اكتب المبلغ"; income type = button reads "ضيف للفلوس" and the
sub-line reads "تذكرة بكره هتكبر"; transfer = two wallet pickers.

### `CloseDay.dc.html` — قفّل اليوم
Title "يومك خلص على خير", the ticket with all its holes and "فضل معاك 72", the torn stub with "+72", three
destinations as radio cards (jar preselected), the button "اقطع التذكرة".
States: leftover 0 = no destinations, copy "قفلتها على الشعرة. شاطر."; over = "عدّيت بـ 35. ولا يهمك، هوزّعهم
على الـ 12 يوم الجايين (3 جنيه في اليوم)." and the button reads "اقطعها وكمّل".

### `Proverb.dc.html` — مثل اليوم (reward)
"التذكرة رقم 47 اتقطعت", "كسبت مثل جديد", the tilted card with set name, number in the set, the proverb and one
line tying it to today, three stat tiles, "شارك المثل" and "تمام، لبكره".
States: all 60 collected = shows a random favourite and "معاك الألبوم كله"; milestone day = a rare card with a
stamp.

### `LastDays.dc.html` — وضع آخر الشهر
Carnelian hero ("فاضل 9 أيام ومعاك 540 · يعني 60 في اليوم"), the row of small daily tickets with the bill day
marked, the two biggest optional habits with pause toggles, the no-spend-day challenge, "لو اتزنقت" (jar, people
who owe you, emergency envelope), the button to switch the mode on until payday.

---

## Where the money goes

### `Roll.dc.html` — الدفتر (tab 2)
Title, search and month buttons, three summary tiles (spent, received, tickets closed), filter chips, then the
roll: today's stub expanded with entries, earlier days as collapsed stubs coloured by result, and non-ticket days
(rent, salary) in neutral or turquoise.
Empty: "الدفتر لسه أبيض" (see `EmptyStates.dc.html`). Search with no match: "ملقتش حاجة بالاسم ده. جرّب كلمة
تانية أو شهر تاني."

### `Wallets.dc.html` — الجيوب
Total and share bar, the cash card with the note counter and "عدّ اللي في جيبك وسوّي الرصيد", the other pockets
(mobile wallet, bank, تحت البلاطة), three quick actions, the SMS card with a parsed example and the privacy line.

### `Envelopes.dc.html` — الخطة · الظروف (tab 3)
Segmented control (الظروف / المواسم / الحصّالة), how the salary was split, the envelope grid, "ظرف جديد", and a
tip about moving money between envelopes.
Empty: "لسه مفيش ظروف. نقسّم مرتبك على 4 ظروف جاهزة؟" with one button.

### `Jar.dc.html` — الحصّالة
The jar drawing filling with coins, the total, the yellow gold card with the user's price, the round-up toggle
with its rounding step, where the money came from, goals, and "فكّ منها" / "حطّ فيها".
Empty: "الحصّالة مستنية أول قرش".

### `Seasons.dc.html` — المواسم
Intro line, the nearest season as a yellow hero with its checklist chips, a timeline of the next occasions, the
honest "كل المواسم مع بعض" card, and the zakat calculator row.

---

## People, prices and rewards

### `People.dc.html` — الناس (tab 4)
Two totals (ليك / عليك), filter chips, the list of people with one action each (فكّره بلُطف / سدّد النوتة), the
lilac duty-ledger card, the gameya card, and the quick-split hint.
Empty: "محدش ليه عندك حاجة".

### `Gameya.dc.html` — الجمعية
The wheel of members (paid out, this month's turn, the user's turn), the user's turn card, this month's
installment and who still owes it, the payout plan, turn swap and share buttons, the "records only" line.

### `Basket.dc.html` — سلّتك
How much the user's own basket rose in three months, the tracked items with last price and change, "تابع سعر
حاجة تانية", the suggestion to raise the ticket, and three habit facts.
Empty: "سجّل نفس الحاجة مرتين وأنا أبدأ أتابع سعرها."

### `Album.dc.html` — ألبومك
Qersh with level and progress to the next, three streak tiles, the proverb grid (collected and locked), stamps,
recent stubs, links to basket, pockets and settings.

### `Match.dc.html` — ماتش الأسبوع
The pitch scoreboard (the user against last week), match events day by day, the hat-trick nudge, the league of
weeks, and "كمّل الماتش".
First week: "أول ماتش ليك. مفيش خصم الأسبوع ده، العب براحتك."

---

## First run

### `Onboarding.dc.html`, `ObPayday`, `ObMoney`, `ObFixed`, `ObTicket`
Five steps. Full flow and rules in `08-ONBOARDING.md`.

---

## Buying, premium, states, icon

### `BeforeBuy.dc.html` — قبل ما تشتري
What you want and its price, "دي تساوي" (tickets, work days, gold, share of the nearest season), "قسط ولا
كاش؟", Qersh's "نام عليها يومين" with the park button, and the "نِفسي فيه" list with the two answers.

### `Paywall.dc.html` — مصروفي بلس
Trial-ending tag, sad Qersh, "بعد بكره، دول هيقفوا", five rows built from the user's own data, the reassurance
that data is never deleted, two prices (year preselected), one button, the plain way out. Rules in
`09-PREMIUM.md`.

### `EmptyStates.dc.html`
Three empty states (roll, jar, people), the "all caught up" row, and the loading skeleton.

### `Icon.dc.html`
Primary icon, sizes, round mask, themed version, alternate. Spec in `13-ICON.md`.

---

## Round 3: subscriptions, one pocket, profile, payday, widgets

### `Subscriptions.dc.html` — الاشتراكات
Monthly total with "4 تذاكر" and the yearly figure, the free-trial warning card (carnelian), Qersh's question of
the month (yellow), the list ordered by next renewal (shared ones show the user's share and what friends owe),
and one-tap chips to add common ones. Reached from the plan tab and from recurring items.
Empty: "مفيش اشتراكات متسجّلة. نت البيت؟ نتفليكس؟ ضيف أول واحد وأنا أفكّرك قبل ما يتجدد."

### `WalletDetail.dc.html` — جيب واحد
Hero in the pocket's colour with the balance and "مطابق لآخر رسالة", four actions, the monthly limit bar (typed
by the user), fees this month, recent entries, and two switches (counts in the ticket, low-balance alert).
Reached by tapping a pocket in `Wallets.dc.html`. A cash pocket shows the note counter instead of limit and fees.

### `Profile.dc.html` — ملفك
The yellow identity card (name, level, three stats, share button), name and birthday and work, the "عيدية منك
ليك" suggestion, household numbers, the main goal as four choices, and the privacy block with the delete action.
Reached from the album and settings.

### `Payday.dc.html` — يوم القبض
Turquoise hero with the amount, then three numbered steps (pay yourself first, settle what you owe, reserve the
fixed costs), the "where the whole salary went" bar, the month's ticket in a yellow card with a strip of mini
tickets, and "اطبع تذاكر الشهر". Appears when a salary-sized income is logged or detected; can be reopened from
the roll's salary row.

### `Widgets.dc.html` — الويدجت
The home-screen widgets (ticket, match, wide with one-tap logging), the locked state with dots, and the evening
notification with its two actions.

---

## Round 4: freelancers, installments, monthly story, kids, settings, lock, backup

### `Freelance.dc.html` — مرتب لنفسك
One line that explains the idea, then the vault card (a drawn tank filled to the buffer level, the amount,
"تكفّي مرتبك شهرين ونص" and the date the salary is safe until), the yellow self-salary card (amount, next payday,
the daily ticket it gives, "عدّل"), six months of income as bars against a dashed salary line (months under the
line in a quiet colour, a month with nothing as a carnelian sliver), the raise suggestion with two answers, money
still owed by clients (overdue ones in carnelian with "فكّره بلُطف"), and the button "جالي شغل · نزّله الخزنة".
Reached from Home (the ticket's source line) and from Profile when the kind of work is freelance.
Empty: "لسه مفيش شغل متسجّل. أول مبلغ ييجي، نزّله الخزنة وأنا أحسبلك مرتب ثابت."

### `Installments.dc.html` — أقساطي
Hero with the monthly total, "44 كل يوم بتتخصم من تذكرتك" and everything still owed. The freedom-date card
("يوم الحرية · فبراير 2028") with a timeline of the months each plan ends and how much the ticket grows at each.
One row per plan (name, "فاضل 7 من 12", day of the month, end month, progress bar). The early-payoff suggestion
for the smallest plan, with two answers. A link to `BeforeBuy.dc.html` for a new purchase.
Reached from the plan tab and from any installment row in the roll.
Empty: "مفيش أقساط عليك. خلّيك كده. ولو ناوي تقسّط حاجة، احسبها هنا الأول."

### `Story.dc.html` — حكاية الشهر
A full-screen yellow story, five slides, segmented progress at the top. Drawn here: slide 2, "قطعت بإيدك 26
تذكرة من 31", with the month as a grid of days (under the ticket, over it, missed), the legend, one line that
compares with last month, the "share without amounts" switch, "شارك الشريحة دي" and the next button.
The other slides use the same frame (one huge number, one drawing, one line):
1. Opening: the month's name and the best day. 3. Where the money went: top three envelopes and the biggest
single hole. 4. The jar: what was saved, cards collected, longest streak. 5. One suggestion for next month and the
shareable summary card.
Appears on the first open after the month closes; reopened from the album. Tap the left half for next, the
right half for back, hold to pause.

### `Kids.dc.html` — مصروف العيال
Monthly total and the handover day, then one coloured ticket per child: name and age, weekly allowance, what is
left until handover day, a row of punches, and below the tear line either their jar (goal, bar, the parent's
match rule) or a pending advance request with two answers. Then the chores list (tick to add a bonus), the
"child screen" hint with Qersh, and the button "سلّم مصروف السبت · 220".
Reached from the people tab. Empty: "ضيف أول طفل ومصروفه في الأسبوع. هيبقى ليه تذكرة صغيرة زي بتاعتك."

### `Settings.dc.html` — الإعدادات
Profile row, the Plus row (trial days left), then four groups of rows: التذكرة (payday, tear-off reminder, where
the leftover goes, round-ups), الإحساس والشكل (language as a two-way switch, vibration, ticket sounds, hide
amounts), الأمان (lock, backup with the last date, read bank messages), عن مصروفي (privacy and terms, feedback,
version). A value on the left of a row opens a small sheet; a switch acts at once.

### `Lock.dc.html` — القفل
Qersh on guard with a lock badge, "وريني إنك إنت", four PIN dots, a 3-column keypad (the same keys as the add
sheet) with the fingerprint key bottom-left in yellow and delete bottom-right, and "نسيت الرقم؟" which leads to
restoring from a backup. The fingerprint prompt opens by itself when the screen appears.
Wrong PIN: the dots shake once and clear, "مش هو. جرّب تاني." After five wrong tries: keys go quiet and a
countdown shows "استنى 30 ثانية".

### `Backup.dc.html` — النسخة الاحتياطية
Status hero (last backup time, ticket count, size, "متشفّرة"), "اعمل نسخة دلوقتي", "رجّع من نسخة", a card with
the weekly automatic backup switch, where it is saved and the backup password, Qersh's warning that a forgotten
password cannot be recovered, the export-to-Excel row, and three steps for moving to a new phone.
Never backed up: the hero turns yellow-dim with "لسه مفيش نسخة. دقيقة واحدة وتذاكرك في أمان."
While backing up: the button becomes a progress bar that fills; the hero updates in place when it ends.

---

## Screens not drawn (build them from the system)

Use the same components; do not invent new patterns.

| Screen | Build it as |
|---|---|
| Entry detail and edit | The add sheet, prefilled, with a delete link |
| New envelope / goal / season / person / gameya | A pushed screen: one question per block, yellow save button |
| Envelope detail | Hero number, bar, entries of that envelope as roll rows |
| Person detail | Totals, history rows, "سدّد" / "فكّره" |
| Duty ledger list | Occasions, each with rows of name and amount |
| Zakat calculator | Inputs as rows, result as a yellow hero |
| Search results | Roll rows with the matched word in yellow |
| New installment / client job / child / chore | A pushed screen: one question per block, yellow save button |
| Child screen (شاشة الطفل) | The child's coloured ticket from `Kids.dc.html` at full width, their jar below, one button to ask for an advance. Leaving it needs the parent's PIN or fingerprint |
| Story slides 1, 3, 4, 5 | The frame of `Story.dc.html`: one huge number, one drawing, one line |
| Restore confirmation | A sheet naming the backup's date and what will be replaced; password field; yellow confirm |

**Deliberately not designed yet:** the shared home (بيتنا, two people on one budget). Do not build it from
guesses; it will get its own screens.
