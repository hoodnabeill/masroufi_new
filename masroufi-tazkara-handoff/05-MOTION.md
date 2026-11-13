# Motion

Motion has one job here: show the user **what just happened**. If a movement does not explain a change, it does
not ship. Most of the screen stays still most of the time.

## Rules

1. **React on touch-down.** Every tappable thing dips the moment the finger lands, not on release.
2. **Springs, not timers.** Anything the finger moves or triggers uses a spring so it can be interrupted and
   reversed mid-flight. No fixed-duration tweens on touch-driven things.
3. **Start from where it is.** An interrupted animation continues from its current position and speed.
4. **Enter and leave the same way.** A sheet that rises from the bottom leaves to the bottom. A pushed screen
   comes from the leading edge and goes back to it.
5. **Bounce only after a throw.** Critically damped by default. Overshoot is reserved for things the user flung
   or for the three reward moments below.
6. **One authored moment per screen.** Do not stagger every card on every screen.
7. **"Remove animations" is honoured:** every movement becomes a 120 ms fade; the reward moments become a still
   image with the same haptic.

## The springs

Named by job. Damping 1.0 = no overshoot. Response = how fast it gets there, in seconds.

| Name | Damping | Response | Use |
|---|---|---|---|
| `press` | 1.0 | 0.15 | Touch-down dip and release |
| `snappy` | 1.0 | 0.24 | Chips, toggles, the segmented thumb, colour changes |
| `move` | 1.0 | 0.38 | Screens, cards expanding, bars growing |
| `sheet` | 0.86 | 0.32 | Bottom sheets and anything dragged and released |
| `reward` | 0.62 | 0.45 | The three reward moments only |

## Buttons and taps (everywhere)

| Element | On press | On release |
|---|---|---|
| Primary button | Scale to 0.97, brightness −6%, `press` | Back to 1.0 |
| Card or row that opens something | Scale to 0.98 | Back, then the transition starts from the card |
| Chip, key, stepper | Scale to 0.94 | Back with `snappy` |
| Tab | Icon dips to 0.9 | Fills in (outline to filled) with `snappy` while the label goes bold |
| The yellow + | Scale to 0.92 | Back; the add sheet rises from the + itself |

A tap that cannot do anything (disabled button) gives a 6 dp horizontal shake and the "reject" haptic, and the
missing field pulses once. Never a silent dead tap.

## Screen transitions

| From → to | Motion |
|---|---|
| Tab to tab | Cross-fade only (140 ms). Tabs are peers; nothing slides. |
| Card → its detail (jar card → jar, season card → seasons, wallet row → wallet) | The card grows into the screen (shared bounds), content fades in behind it. Back shrinks it home. |
| Row → pushed screen | Slides in from the leading edge with `move`; the screen underneath shifts 8% and dims. Follows the back gesture 1:1. |
| Anything → add sheet | Rises from the + with `sheet`; the screen behind scales to 0.96 and dims. Drag down to dismiss: it tracks the finger, a flick is thrown with its velocity, and it rubber-bands if pulled up past the top. |
| Home → close day → reward | A sequence (below), not three unrelated screens. |

## The authored moments

These are the ones worth real care. Each explains one event.

### 1. Punch (logging an expense)
What happened: "your entry went into the ticket and the number dropped."
1. Tap "اخرم التذكرة": the button dips; a heavy haptic fires on the same frame.
2. The sheet drops away with `sheet` (220 ms felt).
3. On the ticket, the next hole punches: a dashed circle fills with Night from the centre out (120 ms), the
   ticket recoils 3 dp downward and springs back (`reward`), and a tiny paper dot falls 24 dp and fades.
4. The big number rolls down digit by digit to the new amount (odometer, 300 ms, digits move downward because
   the value fell).
Total under 700 ms. If the user logs again before it finishes, the roll continues from the current digits.

### 2. Tear (closing the day)
What happened: "the day is done and the leftover went somewhere."
1. Press and hold or tap "اقطع التذكرة": the stub tilts up to 3 degrees following the finger; a soft tick as it
   starts.
2. Release: the stub separates along the perforation (it moves 16 dp down and rotates to −3 degrees with
   `reward`); a strong confirm haptic on separation.
3. The "+72" lifts off the stub and travels along a short arc into the chosen destination's row, which pulses
   turquoise once and whose amount counts up.
4. The screen cross-fades to the reward.

### 3. Card flip (the proverb)
What happened: "you earned something."
1. The card enters face-down, 20 dp below its place, and flips over on its vertical axis (`reward`,
   one overshoot). A milestone haptic lands as it settles.
2. The three stat tiles count up, one after another, 80 ms apart.
3. Nothing else moves. No confetti: the card is the reward.

### 4. A new ticket (first open of the day, and the end of onboarding)
The ticket slides down from under the header like paper feeding out of a machine: 40 dp of travel in three small
steps (it "prints"), with a tick per step, then Qersh pops up over its corner. On ordinary days this is quick
(350 ms). In onboarding it is slower (700 ms) because it is the reveal.

### 5. Weekly match goal
When a day is closed under the ticket, the score digit flips (old digit rotates up and out, new one in) and the
event row slides in from the leading edge. A goal against uses the same motion without the bounce.

## Smaller meaningful changes

| Change | Motion |
|---|---|
| A number changes (any amount) | Digits roll: up when it grows, down when it falls. Never a hard swap. |
| A bar changes | Width springs to the new value with `move`. |
| Toggle | Knob slides with `snappy`; track colour follows. |
| Selecting a chip or plan | Fill cross-fades (`snappy`); the selected one scales 1.0 → 1.04 → 1.0. |
| Expanding a day in the roll | Height springs open; entries fade in 40 ms after. Collapses the same way. |
| Moving money between envelopes | The amount chip follows the finger; on drop both envelope numbers roll. |
| Parking an item for 48 h | The item card shrinks into the "نِفسي فيه" list, which gains a row. |
| Qersh's mood | Cross-fades between faces in 200 ms. He blinks at rest every 4–7 s; nothing else idles. |
| Skeleton → content | Cross-fade 160 ms, no shimmer sweep longer than one pass. |
| Error | The field or card shakes 6 dp twice; the message fades in beneath it. |
| Vault level (freelancer) | The fill rises or falls to the new level with `move` when income lands or the salary is paid. Never sloshes or idles. |
| An installment plan ends | Its row's bar fills, the row folds away, and the per-day number rolls down while the ticket number on Home rolls up. |
| Monthly story slide | The progress segment fills linearly (5 s, pauses while held). Slides cross-fade in 200 ms; the big number counts up once, in 600 ms. Day squares do not stagger. |
| Child's handover | Each child's "left" number rolls up; the punches reset to empty in one step. |
| Advance answered | The request block collapses with `snappy`; the answer shows as one line in its place. |
| Chore ticked | The check draws in, the bonus turns turquoise. |
| PIN digit | The dot fills with `snappy`. Wrong PIN: the row of dots shakes 6 dp twice, then empties. |
| Unlock | The lock screen fades out in 160 ms; Home is already drawn underneath. No zoom. |
| Backup running | The button's fill grows left to right with real progress; when done the hero's check draws in and the time rolls to "دلوقتي". |

## What never animates

- Text reflow, list scrolling physics (leave the platform's), anything decorative on a loop, backgrounds,
  the guilloche patterns, and entrance staggers on ordinary screens.
- Nothing blocks input while it moves.
