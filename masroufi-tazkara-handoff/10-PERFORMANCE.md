# Feeling fast

The app is offline and local, so it can be instant. The user should never wonder whether a tap worked.

## The budget

| Moment | Target |
|---|---|
| Touch-down to visible reaction | Under 50 ms (the press state, always) |
| Tap to the next screen starting to move | Under 100 ms |
| Cold start to the ticket being readable | Under 1 second on a mid-range phone |
| Saving an entry | Feels instant: the UI updates first, storage follows |
| Scrolling the roll with a year of entries | No dropped frames |

## Instant feedback

- **Every tap shows a pressed state on touch-down** (`05-MOTION.md`). This is the first and cheapest speed win.
- **Never wait for storage before updating the screen.** On "اخرم التذكرة": close the sheet, punch the hole and
  roll the number at once, and write to storage in the background.
- **Never disable a button "while saving"** for a local write. Guard against double taps by ignoring a second
  tap for 400 ms, not by greying the button out.
- Keyboard and keypad open with the sheet, not after it.

## Optimistic updates

| Action | Show immediately | If the write fails (rare) |
|---|---|---|
| Log an entry | Hole punched, number down, row in the roll | Roll it back, say "ماتسجّلتش. جرّب تاني." and keep what they typed |
| Delete an entry | Row disappears, number back up, 5 s undo bar | Put it back |
| Close the day | Ticket torn, amount in the jar | Restore the ticket |
| Toggle (round-ups, pause a habit) | New state at once | Flip back with a one-line reason |
| Move money between pockets / envelopes | Both numbers roll | Both roll back |
| Settle a tab or loan | Row marked settled | Unmark |
| Sync to the user's storage | A small "بيتحفظ…" line, never blocking | "المزامنة هتكمّل لما النت يرجع." |

Deletes are undoable for 5 seconds instead of asking "are you sure?". Only restoring a backup over existing data
and deleting a whole pocket ask first.

## Loading: skeletons, never spinners

- A screen that is not ready shows **its own shape in grey**: the ticket outline with a blank number block, rows
  with a round tile and two bars, cards with a title bar. Colours: Raised `#1D2766` and Line `#2A357A` (see the
  sample in `EmptyStates.dc.html`).
- The skeleton has the same sizes as the real content so nothing jumps when it arrives.
- No spinner anywhere in the app. No "جاري التحميل". One soft pulse (opacity 0.6 to 1.0, 900 ms) on the grey
  blocks is enough, and it stops when "remove animations" is on.
- If content arrives in under 150 ms, skip the skeleton entirely (no flash).
- Content replaces the skeleton with a 160 ms cross-fade.

## Keeping it actually fast

- **The ticket is precomputed.** Today's number, spent so far and the holes are stored for the day and updated
  when an entry changes, not recalculated from all history on every open.
- **Home reads one small summary**, not six separate queries. The roll loads the current month first and older
  months as the user scrolls.
- **Start-up shows the last known Home** from the stored summary while fresh values load behind it.
- Lists are virtualised and rows have stable keys. Images are vector; there are no photos to decode except
  receipts, which load as thumbnails.
- Fonts are bundled in the app (Lalezar, Alexandria). Never downloaded at run time.
- Heavy work (monthly statistics, basket index, backup encryption) runs off the main thread and never blocks a
  screen from opening.
- The home-screen widget reads the same stored summary.

## Perceived speed

- Transitions start from the thing that was tapped, so the eye already knows where to look.
- Numbers roll to their new value instead of jumping: it reads as "it changed" rather than "it reloaded".
- Screens never appear empty and then fill; they appear as skeleton or as content.
- After any action the confirmation is in the place the user is already looking (the ticket, the row), not a
  toast at the other end of the screen. Toasts are for undo only.

## Checklist before shipping a screen

1. Does every tappable thing react on touch-down?
2. Is there a skeleton with the right shape?
3. Is there a real empty state with a next step?
4. Does the main action update the screen before storage finishes?
5. Can the action be undone or interrupted?
6. Does it still work at 200% font size and with animations off?
