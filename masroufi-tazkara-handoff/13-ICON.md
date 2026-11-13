# Icon and mascot

Source: `screens/Icon.dc.html` (the icon) and any screen with Qersh on it.

## The app icon: "التذكرة"

Three flat shapes on lapis night:

1. **The ticket:** a yellow (`#FFC72C`) rounded rectangle, about 68% of the icon's width, tilted 10 degrees
   anticlockwise, with two half-circle notches (top and bottom) where the perforation is, two-thirds of the way
   along.
2. **One punched hole:** a night-coloured circle in the larger part of the ticket. It says "you logged
   something".
3. **The fin:** a turquoise (`#35E0D0`) shark fin rising from behind the ticket's top edge. That is Qersh hiding.

Background: `#0A1033`, with faint guilloche circles in one corner at very low contrast (drop them below 96 px).

| Version | Notes |
|---|---|
| Adaptive foreground + background | Keep all artwork inside the middle 66% so any launcher mask is safe |
| Round mask | Scale the artwork to 85% |
| Themed (Android 13+) | One colour: ticket, fin and the hole cut out as a single shape |
| 48 px and below | Drop the dashed perforation line; keep ticket, hole, notches and fin |
| Store icon 512 | The primary, no text |
| Alternate "قرش" | Qersh's face on a yellow field, for a seasonal or Plus alternate icon |

Do not add the name, a letter, a currency sign, a gradient, a shadow or a border.

## Splash

Night background, the icon's ticket alone in the centre at 96 dp. On launch it "prints" down 12 dp and Home's
ticket takes over from the same position, so the splash and Home feel like one motion.

## Qersh (قرش)

The name means both "piastre" and "shark".

### Construction (on a 72 × 72 grid)
- **Body:** a circle, radius 26, centred at (36, 42), yellow `#FFC72C`, outlined 3 px in night `#0A1033`.
- **Coin ring:** an inner circle, radius 19, dashed (2 on, 4 off), in `#C99400`. Drop it below 56 px.
- **Fin:** a curved triangle on the top, leaning toward the trailing side, turquoise `#35E0D0`, same outline.
- **Eyes:** two night dots, radius 3.5, at (28, 38) and (44, 38). A tiny highlight dot at large sizes only.
- **Mouth:** one stroke, 3 px, round ends.

### Moods (only the eyes, brows and mouth change)

| Mood | Face | Used |
|---|---|---|
| Happy (default) | Dot eyes, gentle smile | Home, onboarding questions, match |
| Delighted | Eyes as two upward arcs, open filled mouth | After tearing the ticket, the album, the reveal, purchase done |
| Thinking | Dot eyes, flat mouth | Before you buy, when money is tight |
| Surprised | Dot eyes, small round mouth | Empty roll |
| Sleepy | Eyes as downward arcs, small smile, two little "z" marks | Nothing owed, all caught up |
| Sad | Dot eyes, brows slanting in, downturned mouth | Paywall only |
| Waving | Happy face plus one curved arm line | Welcome |

### Rules
- He appears at moments, never as wallpaper: at most one Qersh per screen.
- He never mocks, scolds or looks angry. When things are bad he looks thoughtful and offers a way out.
- He is always the same yellow coin. On a yellow background his body is a lighter yellow (`#FFE08A`).
- Sizes: 52–64 dp beside a speech bubble, 76 dp peeking over the ticket, 92–96 dp on the paywall and album,
  150–220 dp in onboarding and empty states.
- At rest he blinks every 4–7 seconds. That is his only idle motion.
- He speaks in short lines in a speech bubble: Surface colour, the corner nearest him squared.

## Illustrations for empty states

Flat, same outline weight as Qersh (3 px night), same palette, one object plus Qersh: a blank sheet for the
roll, an empty jar, Qersh asleep. No backgrounds, no shadows, no gradients. See `screens/EmptyStates.dc.html`.
