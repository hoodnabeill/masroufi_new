# Masroufi v2 Phase 12 (polish pass) Implementation Plan

> **For agentic workers:** one implementer subagent per task, one at a time (owner instruction); done when CI is green on its head commit; no reviewer loop unless asked.

**Goal:** Every screen passes the `10-PERFORMANCE.md` checklist (touch-down pressed state, skeletons, empty states, optimistic updates, undo, 200% font size, TalkBack reading order, right-to-left, "remove animations", contrast of all text, 48 dp touch targets), and the known gaps left by earlier phases are closed. Each task lists what it changed.

**Spec:** `12-BUILD-PROMPTS.md` Prompt 12; `10-PERFORMANCE.md` (all, esp. "Checklist before shipping a screen"); `03-DESIGN-SYSTEM.md` (tokens, contrast, touch targets, digits); `05-MOTION.md` (reduced motion); `06-HAPTICS.md`; the carry-forward doc (every "not built", "concern" and "guessed copy" line).

## Global constraints

As Phases 3–11 (strings ar + en with `%s`; Long piastres; injected Clock; the test rules and the DataStore deadlock rule in the carry-forward; poll CI at most once a minute; never push while your own CI run is in progress). No new features. No copy changes except fixing broken text.

## Decisions taken in this plan (owner may override)

1. **Automated checks first.** Task 59 adds a reusable Robolectric/Compose audit harness: render a screen's content composable with sample state at font scale 2.0, in RTL, with animations off, and assert (a) every clickable node is at least 48 × 48 dp, (b) every clickable node has a label TalkBack can read (text or contentDescription), (c) nothing is clipped that holds text (no `TextLayoutResult.hasVisualOverflow` on single-line amounts where avoidable), and a token-level contrast test (every text colour token on every background token it is used on ≥ 4.5:1, large text ≥ 3:1). Tasks 60–62 run it on their screens and fix what fails.
2. **Manual items** the harness cannot prove (pressed state on touch-down, skeleton shape, optimistic update, undo, reading order) are checked by reading each screen's code against the checklist; each task's report has one row per screen with the six checklist answers.
3. **Known gaps** from earlier phases are closed in Task 59 when small and shared; screen-specific ones go to the task that owns the screen.

---

## Task 59: Audit harness, contrast, shared gaps

- The harness and the contrast test (decision 1), with a short doc comment on how to use it.
- Shared gaps: finish hide amounts on the screens Task 55 left (Album, Basket, Before you buy, Close day / End of month / Freelance / Installments / Kids text lines, Match, onboarding reveal, Payday ritual, Paywall, Profile, Proverb reward, Story) through `poundsText`; the duplicate notification id for two of one kind on a day (unique id per item); "فاتورة فاتورة X" (do not prefix a name that already starts with the word); the main Settings reminder sheet asks for POST_NOTIFICATIONS like the notifications screen; the 30-day rare card uses the same streak rule as Home (safety ticket when Plus); editing an entry shows and keeps its item and unit price; envelope screens do not list the KIDS item as a bill row.
- Tests for each gap fixed; harness self-test on one known-good and one known-bad composable.

## Task 60: Daily loop screens

- Home (all card states), the add sheet, close day, proverb reward, the roll (incl. search and edit), pockets and one pocket, the new-pocket screen, onboarding (all steps), the lock screen, the child screen: run the harness, fix failures, read each against the checklist, fix what fails, report the table.

## Task 61: Plan screens

- Plan tab (envelopes, envelope detail/form, jar and goals, seasons and zakat, subscriptions, fixed costs), payday ritual, installments, before you buy and the wishlist, freelance, end-of-month mode: same as Task 60.

## Task 62: People, rewards and settings screens

- People, person detail, duty ledger, gameya and its form, kids and child form, album, match, basket, profile, story, paywall, settings, legal, lock settings, backup, notifications settings, the widgets' content mapper: same as Task 60.

## Task 63: Soft delete with undo everywhere (added after Task 61)

- `11-DATA-AND-RULES.md` "Safety rules": deleting is soft for 5 seconds (undo), then real. Today only entries (the roll, Home) do this through `PendingDeletes`. Extend the same pattern to every other delete the user can make: fixed costs, subscriptions, installment plans, client jobs, goals, seasons and season items, envelopes (keep their confirm only where data would move), people, debts, tab items, occasions and gifts, gameya plan rows, chores, children, tracked basket items, wishes. One shared undo bar ("اتمسحت. رجّعها؟"), Heavy haptic on the real delete, the item hidden at once and restored on undo. Saves that still wait for storage before the screen updates (Task 61's checklist item 4) get the optimistic pattern where it is a one-line change; list the rest.
- Tests: each delete hides at once, undo restores, the real delete happens after 5 s on virtual time.
