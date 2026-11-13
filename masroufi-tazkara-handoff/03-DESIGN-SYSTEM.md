# Design system

The mocks in `screens/` are the source of truth. This file names the decisions so they are applied the same way
everywhere. 1 px in a mock = 1 dp. Text sizes are sp.

## The look in one line

Lapis night, a metro-ticket yellow, and big chunky numbers. Flat colour blocks, no gradients, no shadows on
content. The only "material" is paper: tickets, stubs, envelopes, stamps.

## Colour

| Name | Hex | Use |
|---|---|---|
| Night (ground) | `#0A1033` | Screen background, punched holes, text on yellow |
| Surface | `#141C4F` | Cards, nav bar, sheets |
| Raised | `#1D2766` | Keys, inner tiles, icon tiles on cards |
| Line | `#2A357A` | 1 dp card borders, dividers, tracks |
| Dashed line | `#3A4590` | Dashed "add" outlines, locked items, future days |
| Text | `#F2F3FF` | Primary text on night |
| Text 2 | `#AEB6E6` | Secondary text, captions |
| Text 3 | `#8189C4` | Hints, locked labels (never for body text) |
| **Ticket yellow** | `#FFC72C` | The ticket, primary buttons, money, selected tab |
| Yellow ink | `#3D2F00` / `#5C4700` | Secondary text on yellow |
| Yellow rule | `#B38600` / `#E3AB00` | Perforations and guilloche lines on yellow |
| Yellow dim | `#3A2F0A` | Tinted yellow panels on night (tips) |
| **Turquoise** | `#35E0D0` | Good news: under the ticket, saved, paid, progress |
| Turquoise ink | `#06282B` | Text on turquoise |
| Turquoise dim | `#0F3B4A` | Tinted good-news panels; text on it `#D6F7F3` |
| **Carnelian** | `#FF6B4A` | Over the ticket, "about to stop", end-of-month hero |
| Carnelian text | `#FF9A82` | Carnelian as text on night |
| Carnelian dim | `#40182A` | Tinted warning panels |
| Carnelian ink | `#2B0A02` | Text on carnelian |
| Sky | `#8FB4FF` | Fourth data colour (bank, transport) |
| Lilac | `#E7A6FF` | Fifth data colour (people, the duty ledger) |

Rules:
- Yellow means money and the one main action on a screen. One yellow button per screen.
- Turquoise and carnelian carry meaning (good / careful). Never use them as decoration.
- Colours that must be told apart also differ in lightness. Never rely on colour alone: selected states also
  change weight or fill; over/under also changes the words.
- Body text contrast is at least 4.5:1. Text 3 is for 11–12 sp labels on Surface only.

## Type

| Face | Use |
|---|---|
| **Lalezar** (one weight) | Display: every number, screen titles, buttons, card headings |
| **Alexandria** 400/500/600/700 | Everything else: labels, body, captions |

| Role | Face / size / line | Where |
|---|---|---|
| Ticket number | Lalezar 104, line 0.95, tracking −2 | Today's amount |
| Hero number | Lalezar 56–88 | Jar total, close-day leftover, paywall prices |
| Card number | Lalezar 28–40 | Envelope amounts, summaries |
| Row number | Lalezar 18–22 | List amounts, chips |
| Screen title (tab) | Lalezar 30 | الدفتر، الخطة، الناس |
| Screen title (pushed) | Lalezar 22, centred | الجيوب، المواسم |
| Card heading | Lalezar 20 | "أسبوعك" |
| Button | Lalezar 18–24 | |
| Body | Alexandria 14–15 / 1.7 | |
| Label | Alexandria 13, weight 500–700 | |
| Caption | Alexandria 11–12 | |

- Digits are Western (0–9) in both languages, always tabular so changing numbers do not jitter.
- Arabic is never letter-spaced. Arabic body line height is at least 1.6.
- The currency ("ج" or "جنيه") is set smaller and in Text 2 beside a number, never the same size.

## Shape and spacing

- Radii: chips and keys 18, cards 24, hero cards and the ticket 28, sheets 32 on the top corners, buttons and
  pills fully round. Icon tiles 14.
- Spacing steps: 4, 8, 12, 14, 16, 20, 24. Screen gutter 16. Card padding 16 (hero 20). Gap between cards 14–16.
- Touch targets are at least 48 dp. A control may look smaller (34–44) but its touch area is 48.
- Borders: 1 dp Line on Surface cards. Dashed 1.5 dp for "add", "locked" and "future".

## Paper motifs (the signature)

- **Ticket:** yellow, radius 28, a dashed perforation with a half-circle notch cut into each side, punched holes
  as Night-coloured circles (26 dp), unpunched as dashed outlines.
- **Stub:** the piece below the perforation. Torn stubs are tilted 2–3 degrees.
- **Guilloche:** faint concentric circles in a corner of yellow or turquoise surfaces, like banknote engraving.
  Decoration only, low contrast, never behind small text.
- **Envelope:** a card with square top corners and a V-shaped flap line in the envelope's colour.
- **Stamp:** a double-ring circle, tilted, with a short Lalezar label. Earned stamps are coloured; locked ones
  are dashed.
- **Proverb card:** a tall solid-colour card (300 × 400 in the reward), tilted 2 degrees.

## Components

| Component | Spec |
|---|---|
| Primary button | Yellow fill, Night text, Lalezar 20–24, height 56–62, fully round. One per screen. |
| Secondary button | 1.5 dp Line outline, Text colour, same height. |
| Text link | Yellow, Alexandria 13. |
| Chip (choice) | Height 40–48, pill. Selected: filled (Text on Night for filters; yellow for choices). |
| Segmented control | Surface track with Line border, yellow thumb, Lalezar 17. |
| Card | Surface, Line border, radius 24, padding 16. |
| Row | 44–46 dp leading tile or avatar, title 15/600, caption 12 Text 2, trailing Lalezar number. |
| Toggle | 48 × 28 track; on = turquoise track with dark knob at the end; off = Dashed-line colour track. |
| Sheet | Surface, top radius 32, 40 × 5 grabber, dimmed Night scrim behind. |
| Tab bar | Floating, Surface, radius 26, height 72, four tabs and a 56 dp yellow add button in the middle. |
| Progress bar | Height 6–14, round ends, Line track. Multi-part bars leave 3 dp gaps. |
| Mascot bubble | Surface bubble beside a 64 dp Qersh, the corner nearest him squared to 6. |

## Icons (treated like type)

- One family: 24 × 24 grid, **1.9 dp stroke**, round caps and joins, no fills, optically centred. Draw new icons
  in this style; never mix in another set, never use emoji.
- **Selected state = the filled version** of the same icon (in the tab bar the selected tab is filled yellow and
  its label is bold). Unselected is the outline in Text 2. Do not signal selection with colour alone.
- Sizes: 24 in the tab bar, 20–22 in tiles, 18 beside text, 14 in captions. Stroke stays visually constant: use
  2.2 at 18 and below.
- Inside a coloured tile the icon takes the tile's ink colour.
- Every icon-only button has a text label for screen readers.

## The mascot, Qersh (قرش)

A yellow coin with a dashed inner ring, two dot eyes, a small smile, and a turquoise shark fin on top, outlined in
Night. He appears at moments, not everywhere: peeking over the ticket, in onboarding, in empty states, on the
paywall, in the match. Moods: happy (default), delighted (closed eyes, open mouth: after closing a day), thinking
(flat mouth: before you buy), sleepy (empty states), sad (paywall, slanted brows). Full spec in `13-ICON.md`.

## Accessibility

- Right-to-left first. Numbers and the keypad stay left-to-right.
- Works at 200% font size: cards grow, nothing clips. Lalezar numbers shrink to fit their line before wrapping.
- TalkBack: the ticket reads as one sentence ("معاك النهارده 156 جنيه من 240، صرفت 84 في 3 مرات").
- "Remove animations" turns every movement into a short fade.
- Real buttons and links everywhere; nothing is tappable without a role and a label.
