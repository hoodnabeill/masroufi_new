# Onboarding

Goal: from first open to the user's own ticket in under a minute, and to their first logged expense in the same
sitting. Three questions, each answerable with one tap or one number. Nothing asked that is not needed to print
the ticket.

Screens: `Onboarding.dc.html` → `ObPayday.dc.html` → `ObMoney.dc.html` → `ObFixed.dc.html` → `ObTicket.dc.html`.

## Principles

- **One question per screen**, in big type, in Qersh's voice.
- **Tell them why, in one line**, in Qersh's speech bubble. People answer money questions when they know the
  reason.
- **Defaults are picked for them** where a common answer exists (25th, 9 pm).
- **Everything can be skipped or approximated.** "بالتقريب كفاية." Exact numbers come later, on their own.
- **Progress is visible:** three bars, one per question. The welcome and the reveal are not counted.
- **No account, no email, no permissions up front.** The only permission (notifications) is asked at the moment
  the user picks a reminder time, and only if they pick one.
- **End on the product, not on a "done" screen.** The last button opens the add sheet.

## The flow

### Step 0: Welcome (`Onboarding.dc.html`)
- Yellow screen, big Qersh waving. "أهلاً! أنا قرش" / "شغلتي إن فلوسك تكمّل لآخر الشهر. كل يوم هديك تذكرة فيها
  تقدر تصرف كام."
- Expectation set: "3 أسئلة صغيرة وتذكرتك تطلع. أقل من دقيقة."
- One button: "يلا بينا". A language switch in the corner. The privacy line under the button.
- Motion: Qersh waves once on arrival (his arm line rotates 20 degrees and back). Nothing else moves.

### Step 1: Payday (`ObPayday.dc.html`)
- "بتقبض يوم كام؟" with eight one-tap answers (1, 5, 10, 15, 25, 28, آخر الشهر, يوم تاني) and a dashed row for
  "مش ثابت" (daily wage, freelance, allowance).
- Bubble: "عشان أحسب تذكرتك لحد القبض الجاي."
- As soon as an answer is tapped the turquoise line updates: "يعني فاضل 13 يوم على القبض الجاي." That instant
  echo is the point: the user sees the app already working.
- "يوم تاني" opens a day picker (1–31). "مش ثابت" sets a rolling 30-day window and later asks "آخر مرة جالك
  فلوس إمتى؟" only when it matters.

### Step 2: What you have (`ObMoney.dc.html`)
- "معاك كام دلوقتي؟" with the same keypad as the add sheet, and +100 / +500 / +1,000 shortcuts.
- Bubble: "بالتقريب كفاية. محدش هيشوف الرقم ده غيرك."
- Caption: "الكاش والمحفظة والبنك كلهم مع بعض". The split into pockets happens later, from the pockets screen.
- The amount goes into one starting pocket named "الكاش" unless the user changes it later.
- Skipping is allowed ("مش عايز أقول دلوقتي"): the ticket then starts at a neutral starter value and Home asks
  again gently after the first few entries.

### Step 3: Fixed costs (`ObFixed.dc.html`)
- "إيه اللي لازم يتدفع قبل القبض؟" as chips: الإيجار، النت، الجمعية، قسط، الكهربا والغاز، دروس العيال، اشتراك
  الجيم، حاجة تانية.
- Tapping a chip asks for its amount in a small inline field with the keypad; the chip then shows the amount.
  Tapping it again removes it.
- Bubble: "هحجزهم على جنب عشان متصرفهمش بالغلط."
- A running total: "محجوز لحد دلوقتي 2,750".
- Two ways forward: "اطبعلي التذكرة" and the plain link "مفيش حاجة، كمّل".
- Each chosen item becomes a recurring item due before payday (the user sets exact dates later).

### Step 4: The reveal (`ObTicket.dc.html`)
- The ticket prints (see `05-MOTION.md`, moment 4, slow version) showing the number: "240 جنيه" and "تقدر
  تصرفهم كل يوم وإنت مطمّن." The stub shows the arithmetic in three small facts: معاك 5,870 · محجوز 2,750 ·
  13 يوم. Showing the working builds trust.
- One short paragraph teaches the whole loop: "كل ما تصرف حاجة، سجّلها وهتخرم التذكرة. آخر اليوم اقطعها، واللي
  يفضل يتحوّش."
- Reminder question in a card: "أفكّرك تقطعها الساعة كام؟" 8 م / 9 م (selected) / 10 م / من غير. Choosing a
  time triggers the system notification permission; "من غير" skips it. Promise: "تنبيه واحد في اليوم. مش
  هزعجك بغيره."
- The only button: "اخرم أول خُرم", which opens the add sheet over Home. The first save plays the punch with a
  milestone haptic and Qersh says "أول خُرم. يوم جديد."

## After onboarding (first week, on Home, one at a time)

Teach by doing, never with a tour or coach marks covering the screen.

| When | A single dismissible card on Home |
|---|---|
| After the first entry | "شاطر. دوسة واحدة على «بتتكرر معاك» بتسجّل نفس الحاجة تاني." |
| First evening | "أول تذكرة ليك. اقطعها وشوف الباقي هيروح فين." |
| Day 2 | "عايز تفصّل فلوسك؟ الكاش لوحده والمحفظة لوحدها من «الجيوب»." |
| Day 3 | "قسّم مرتبك على ظروف: أكل، مواصلات، فواتير." |
| Day 5 | "أول ماتش ليك بدأ. إنت ضد الأسبوع اللي فات." |
| Day 7 | The 14-day Plus trial is mentioned once: what is included, when it ends, no card required. |

Rules: one card at a time, each can be closed forever, and none appears while end-of-month mode is on.

## Edge cases

- **Money is less than the fixed costs:** the ticket would be zero or negative. Do not show a zero ticket. Show
  Qersh thinking and: "اللي معاك أقل من اللي لازم يتدفع بـ 400. ولا يهمك، نرتّبها سوا." and open end-of-month
  mode with the bills listed.
- **Payday is today or tomorrow:** "قبضك قرّب. نحسب التذكرة من بعد القبض؟" with two answers.
- **Returning user restoring a backup:** the welcome screen has a quiet link "عندي نسخة احتياطية" that skips
  the questions.
- **Leaving mid-flow:** answers so far are kept; reopening continues at the same step.
- **Language switch:** switches immediately and stays on the same step.

## What to measure (locally, no tracking SDK)

Keep these counters on the device only, for the developer's own debugging builds: time from first open to
ticket, the step where people stop, and whether the first entry happens in the first session.
