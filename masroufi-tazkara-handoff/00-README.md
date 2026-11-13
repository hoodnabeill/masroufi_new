# Masroufi "Tazkara" — handoff package

Everything needed to start a clean project and build the new Masroufi (مصروفي). No app code is included on purpose:
this folder is the design, the rules, the words, the motion and the build prompts.

## The idea in one paragraph

Every day is a ticket (تذكرة), like a yellow Cairo metro ticket. Each morning the app prints today's ticket with
one number on it: what you can spend today so your money lasts until payday. Every expense you log punches a hole
(خُرم) in the ticket. At night you tear it off (اقطع التذكرة). Whatever is left drops into your savings jar
(الحصّالة) and you win a collectible Egyptian money proverb. A small mascot, Qersh (قرش: a coin with a shark fin,
because the word means both "piastre" and "shark"), reacts to how you are doing.

## What is in this folder

| File | What it holds |
|---|---|
| `01-PRODUCT.md` | Who it is for, the promise, principles, voice |
| `02-FEATURES.md` | Every feature and how it behaves, free vs Plus |
| `03-DESIGN-SYSTEM.md` | Colours, type, shapes, spacing, components, icon rules |
| `04-SCREENS.md` | Every screen: layout, content, states |
| `05-MOTION.md` | What animates, why, and with which spring |
| `06-HAPTICS.md` | Which touch gets which vibration |
| `07-COPY.md` | Voice rules and the actual words: buttons, empty states, errors, notifications |
| `08-ONBOARDING.md` | The first-run flow, step by step |
| `09-PREMIUM.md` | Masroufi Plus: what is paid, prices, paywall rules |
| `10-PERFORMANCE.md` | Instant feedback, skeletons, optimistic updates |
| `11-DATA-AND-RULES.md` | The data to store and every calculation, in plain words |
| `12-BUILD-PROMPTS.md` | Copy-paste prompts to build it phase by phase |
| `13-ICON.md` | The app icon and mascot spec |
| `14-PROVERBS.md` | The collectible proverb cards |
| `screens/` | The 36 designed screens (`.dc.html`) and `canvas.json` |

## How to look at the screens

The `screens/*.dc.html` files are the visual source of truth. They are Design canvas artboards: open the canvas at
https://claude.ai/artifact/C3bZVjMWZTKSRE5nWYrPbi (private to the owner), or import the files into a new Design
canvas. Read the exact sizes, colours and spacing from the inline styles: 1 px in the mock = 1 dp in the app.

## How to start the new project

1. Create an empty project and put this folder in it.
2. Open `12-BUILD-PROMPTS.md` and paste Prompt 0. It tells the assistant to read these files first.
3. Build one phase at a time. Do not start a phase until the one before it runs.

## Decisions already made

- Android first, native, offline-first. No account, no server, nothing leaves the phone unless the user exports or
  syncs. (Recommended stack: Kotlin + Jetpack Compose + Room. The package is written so another stack can follow it.)
- Arabic (Egyptian colloquial) first, English second. Right-to-left layout. Western digits.
- Dark only: the lapis night background is part of the identity.
- One icon family, one display face, one text face (see `03-DESIGN-SYSTEM.md`).

## Decisions still open (yours)

- Final prices for Masroufi Plus (the mock shows 399 EGP a year and 49 EGP a month as a proposal).
- Trial length (14 days proposed).
- Whether the gold price stays manual or can be fetched when the user asks (the app is offline by default).
- The final list of 60 proverbs: `14-PROVERBS.md` has 50 drafted and needs a native review.
- Kids' allowance: one child free and more in Plus is a proposal; so is the cap of half a week on advances.
- The shared home (بيتنا, two people on one budget) is left out on purpose and has no screens yet.

## Sample data warning

Every name, amount, date and count in the screens is sample data. Do not ship any of it as a default, a claim, a
testimonial or a statistic.
