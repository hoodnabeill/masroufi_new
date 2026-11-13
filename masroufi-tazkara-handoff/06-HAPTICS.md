# Haptics

Every action is acknowledged, and the strength matches how much it matters. Light for ordinary taps, firm for
confirmations, a pattern for milestones. Haptics fire on the **same frame** as the visual change they belong to.

## The five levels

| Level | Feel | Android constant to map to | When |
|---|---|---|---|
| **Tick** | Barely there | `CLOCK_TICK` / `KEYBOARD_TAP` | Keypad digits, chips, toggles, tab switches, steppers, each step of the ticket printing |
| **Tap** | Light click | `VIRTUAL_KEY` / `CONTEXT_CLICK` | Opening a card, selecting a plan, picking a destination |
| **Confirm** | One firm click | `CONFIRM` | Entry saved (the punch), settings saved, money moved between pockets or envelopes |
| **Heavy** | Strong single thud | `LONG_PRESS` or a 30 ms strong one-shot | The ticket tearing, paying off a tab, deleting after confirmation |
| **Milestone** | A short pattern: light, light, strong | Custom waveform, about 20 ms · gap 60 · 20 ms · gap 60 · 45 ms | Proverb card flip, hat-trick, a new level, a full week, first gram of gold |
| **Reject** | Two quick dull taps | `REJECT` | Disabled button tapped, wrong PIN, invalid amount |

## Map by action

| Action | Haptic |
|---|---|
| Keypad digit, backspace | Tick |
| Tab change | Tick |
| Chip / segmented choice | Tick |
| Toggle on or off | Tick |
| One-tap repeat chip on Home | Confirm (it saves an entry) |
| "اخرم التذكرة" | Confirm, on the frame the hole fills |
| Start tearing the ticket | Tick |
| Ticket separates | Heavy |
| Leftover lands in the jar | Tap |
| Proverb card settles | Milestone |
| Weekly match goal | Confirm; goal against: Tap |
| Hat-trick, new level, full week, stamp earned | Milestone |
| Drag an amount between envelopes: pick up / drop | Tap / Confirm |
| Settle a tab or a loan | Heavy |
| Park an item for 48 h | Confirm |
| "صرفت نظر" (amount goes to the jar) | Milestone |
| Purchase of Plus completes | Milestone |
| Pull past the end of a list or sheet | none (the rubber-band is the feedback) |
| Error, disabled tap, wrong PIN | Reject |
| Delete confirmed | Heavy |
| Work income lands in the vault | Confirm |
| Self-salary paid from the vault | Heavy |
| Raise accepted | Milestone |
| An installment plan fully paid, early payoff confirmed | Milestone |
| Story: next or previous slide | Tick |
| Story: "best month" line appears | Milestone (once per story) |
| Allowance handed over | Heavy |
| Advance approved or declined | Confirm |
| Chore ticked | Confirm |
| Child's jar reaches its goal | Milestone |
| PIN digit | Tick |
| Unlock succeeds | Tap |
| Backup or restore finishes | Confirm |

## Rules

- Never two haptics for one action. The strongest one wins.
- No haptic for scrolling, for screens appearing, or for anything the user did not cause.
- Respect the system setting: if touch feedback is off in the phone's settings, the app is silent too.
- Add one in-app switch, "الاهتزاز", on by default, in Settings.
- Devices without a good vibration motor get Tick and Confirm only; skip the Milestone pattern rather than
  playing it badly.
- When "remove animations" is on, haptics still fire. They become the main feedback.
- Milestones are rare by design. If a session would fire more than two, queue the second for the next screen.

## Sound

Off by default. One optional switch, "صوت التذكرة": a paper punch on logging and a tear on closing the day, both
under 200 ms and quiet. Never any other sounds.
