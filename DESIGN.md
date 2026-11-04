# Masroufi design system — "Apple-native" (redesign/apple)

Replaces the Midnight Ledger look. The product, copy, features and layouts stay; the visual world and the
interaction model follow Apple's Human Interface Guidelines and *Designing Fluid Interfaces* (WWDC 2018),
built natively in Jetpack Compose.

## Principles

1. **Content first, chrome floats.** Screens are grouped content on a grouped background. Only chrome that
   floats over content (tab bar, add button, snackbars, sheets) is Liquid Glass.
2. **Everything is a spring, everything is interruptible.** No fixed-duration tweens on anything the finger
   touches. Critically damped by default; bounce only after a flick.
3. **Feedback on touch-down.** Every tappable thing dims/dips the instant it is pressed.
4. **One tint.** Green is money and the primary action. Indigo is links and navigation accents. System colours
   for everything else. No decorative gradients.
5. **Light and dark are both first-class**, following the system (Settings can force one).

## Colour (iOS system palette)

| Token | Light | Dark | Use |
|---|---|---|---|
| base | #F2F2F7 | #000000 | systemGroupedBackground, the screen |
| fill | #FFFFFF | #1C1C1E | secondarySystemGroupedBackground, cards and cells |
| fillStrong | #E9E9EE | #2C2C2E | tertiary fill, inputs, inner tiles, steppers |
| track | #E3E3E8 | #38383A | progress tracks, switch off |
| separator (strokeBottom) | #C6C6C8 | #38383A | hairline dividers inside cards |
| label | #000000 | #FFFFFF | primary text |
| secondaryLabel | #6C6C70 | #AEAEB2 | captions, metadata (≥4.5:1 on fill) |
| tertiaryLabel | #8A8A8E | #8D8D93 | hints, disabled |
| primary (green) | #248A3D | #30D158 | money, primary buttons, switches |
| secondary (indigo) | #5856D6 | #7D7AFF | links, selection, brand |
| warning (orange) | #C93400 → text, #FF9500 fill | #FF9F0A | soon, streak |
| over (red) | #D70015 | #FF453A | over pace, destructive |
| transfer (blue) | #0066CC | #0A84FF | transfers |

Categories use the 12 iOS system hues (red, orange, yellow, green, mint, teal, cyan, blue, indigo, purple,
pink, brown) in their light/dark variants. Category icons are **solid squircle tiles with a white glyph**
(the Settings-app idiom).

## Type

IBM Plex Sans Arabic for all text (it carries Latin); Readex Pro (rounded, like SF Rounded) for money and big
numbers, always tabular. Apple's ramp, mapped to Material roles:

| Role | Apple style | Size/leading | Weight |
|---|---|---|---|
| headlineLarge | Large Title | 34/41 | Bold |
| headlineMedium | Title 2 | 22/28 | Bold |
| headlineSmall | Title 3 | 20/25 | SemiBold |
| titleLarge / titleMedium | Headline | 17/22 | SemiBold |
| titleSmall | Subheadline emph. | 15/20 | SemiBold |
| bodyLarge | Body | 17/22 | Regular |
| bodyMedium | Subheadline | 15/20 | Regular |
| bodySmall | Footnote | 13/18 | Regular |
| labelLarge | Button | 17/22 | SemiBold |
| labelMedium | Caption 1 | 12/16 | Medium |
| labelSmall | Caption 2 | 11/13 | Medium |

Latin tracking is size-specific (−0.02em at display sizes, −0.01em at 15–17, 0 below). Arabic is never tracked
and gets ~18% taller leading.

## Shape

Continuous ("squircle") corners everywhere, from `ContinuousShape`: icon tile 22.5%, controls 12, tiles 16,
cards 22, sheets and hero 28. Buttons and chips are capsules.

## Motion (Motion.kt values, response in seconds)

| Spec | Damping | Response | Use |
|---|---|---|---|
| spatial | 1.0 | 0.38 | position, size, layout |
| snappy | 1.0 | 0.24 | small state changes, pills |
| effects | 1.0 | 0.5 | colour, opacity |
| bouncy | 0.72 | 0.34 | only after a flick or a drag release |

Gestures track the finger 1:1, hand their release velocity to the spring, project momentum
(`v/1000·0.998/0.002`) to pick a target, and rubber-band at edges. The tab bar's selection lens can be
dragged between tabs and thrown. "Remove animations" turns every spring into a snap.

## Materials

- **Cards / cells:** solid `fill`, no border, no shadow (grouped-list idiom).
- **Liquid Glass (floating chrome only):** live blur + lens refraction + specular rim on Android 12+, solid
  `fill` otherwise or when "Solid surfaces" is on.
- **Sheets:** `fill` pane, 28 dp continuous top corners, 36×5 grabber, system blur behind.

## Navigation

iOS 26 layout: a floating glass capsule tab bar (Home · Activity · Plan · More) with a sliding, draggable
selection lens, and a separate round glass **+** beside it. Tab roots open with a Large Title; pushed screens
use an inline centred title with a round glass back button.
