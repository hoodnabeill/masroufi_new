# Masroufi v2 Phase 9a (installments and freelancer mode) Implementation Plan

> **For agentic workers:** one implementer subagent per task; done when CI is green on its head commit (owner instruction: no reviewer loop unless asked).

**Goal:** أقساطي — every installment plan in one place with the freedom date and early payoff; مرتب لنفسك — freelancer mode where work income lands in the vault and a fixed self-salary runs the payday ritual.

**Spec:** `12-BUILD-PROMPTS.md` Prompt 9a; `02-FEATURES.md` §28–29; `11-DATA-AND-RULES.md` ("Freelancer mode", "Installments", "Payday ritual"); `04-SCREENS.md` "Freelance", "Installments"; mocks `screens/Installments|Freelance.dc.html`; `05-MOTION.md` (vault level, an installment plan ends); `06-HAPTICS.md` (income into vault Confirm, self-salary Heavy, plan fully paid / early payoff Milestone); `07-COPY.md`.

## Global constraints

As Phases 3–9 (Migrations only, never edit old ones; strings ar + en; copy verbatim; Long piastres; injected Clock; the test rules in the carry-forward "Phase 8 notes"; poll CI at most once a minute; never push while your own CI run is in progress).

## Decisions taken in this plan (owner may override)

1. **Installments are recurring items of kind INSTALLMENT** (they already exist, and onboarding creates them). Migration 6→7 adds `recurring.totalCount` and `recurring.paidBefore` (payments made before the app tracked it). Paid count = `paidBefore` + entries logged against the plan, so undoing (deleting) a payment entry fixes the count by itself — no stored counter to drift. A plan with no `totalCount` is open-ended (shown without an end month; not part of the freedom date).
2. **On its day** (first open on or after the plan's `nextDue`, inside `printToday`), an active installment logs its entry from its pocket and advances `nextDue` (the existing bill model); idempotent; the roll's 5-second undo covers it. When the paid count reaches the total, the plan is finished (Milestone haptic next time the screen is seen).
3. **Early payoff** = one entry for the confirmed amount against the plan (no punch, like any recurring payment) plus a MANUAL jar withdrawal of the same amount; the plan is marked finished (`paidBefore` raised so paid = total). Offered only for the plan with the least remaining, only when the free jar (not earmarked for goals or seasons) covers it, at most once a month per plan (settings key); "كمّل عادي" hides it for the month.
4. **Freelancer mode** lives in settings: on/off, self-salary, self-payday (day of month). Turning it on creates the vault pocket (`PocketKind.VAULT`, never counts in the ticket) if none exists and sets the pay cycle to a fixed payday on the self-payday day. It is turned on from the Freelance screen, reached from Profile's "شغل حر" and from Home's ticket source line; onboarding's "مش ثابت" path is unchanged (builder's call: one entry point to build and test).
5. **Self-payday:** on the first open on or after the self-payday (once per month, settings key), `printToday` moves min(self-salary, vault) from the vault to the lowest-id counting pocket (a MOVED entry, no fee), then the payday ritual is offered as for a salary (the existing trigger is extended to this move). Short vault: pay what is there, say so plainly on Home (builder's wording), never negative.
6. **Client jobs:** table `client_job` (id, client, label, amount, dueDate, paidEntryId?). Marking paid creates a RECEIVED entry into the vault. Overdue the day after the due date. "فكّره بلُطف" = the share sheet with a polite message (as People).
7. **Raise/lower suggestions** per `11-DATA` (six-month average income into the vault; buffer from the vault; at most once a month; "خلّيه زي ما هو" silences for three months); applying changes the self-salary only on the user's tap.

---

## Task 47: Installments and freelancer data

- Migration 6→7 (decisions 1 and 6) with a migration test.
- `InstallmentRepository`: plans with paid/left/end month/progress, monthly total, per day (÷ 30, nearest pound), remaining, freedom date (latest end month), timeline (each end month with the ticket growth = monthly ÷ 30 floored), add/edit/delete plan (with `totalCount`, `paidBefore`), the day-of logging in `printToday` (decision 2), early payoff suggestion and apply (decision 3).
- `FreelanceRepository`: on/off (decision 4), vault total, buffer months rounded down to the nearest half, "safe until" (today plus that many self-paydays), self-salary and next self-payday with the daily ticket it gives (from the ticket rules), the self-payday move (decision 5) incl. the short case, six months of vault income by month with the average, raise/lower suggestions (decision 7), client jobs CRUD / overdue / mark paid (decision 6), "جالي شغل" (RECEIVED into the vault).
- Tests (as Prompt 9a asks): buffer months (incl. halves), freedom date, ticket growth, vault short on payday; plus day-of logging and undo, paid count from entries, early payoff conditions, raise/lower rules and silencing, overdue.

## Task 48: Installments and Freelance screens

- Installments per `Installments.dc.html`: hero (monthly total, "N كل يوم · بتتخصم من تذكرتك", everything still owed), freedom-date card with the timeline and the growth line, plan rows (name, "فاضل N من M · يوم D كل شهر · تخلص {month}", amount, progress bar), the early-payoff card with its two answers (amount confirmed in a keypad sheet), the link to before-you-buy, add/edit plan (pushed form). Empty state from `04-SCREENS.md`. Reached from the Plan tab (a link next to the others) and from installment rows in the roll. Motion when a plan ends (`05-MOTION`).
- Freelance per `Freelance.dc.html`: the idea line, the vault card (drawn tank at the buffer level, amount, "تكفّي مرتبك …", "safe until" line), the yellow self-salary card (amount, next payday, the daily ticket, "عدّل"), six months of income bars against the dashed salary line (under the line quiet, empty month a carnelian sliver), the suggestion card with two answers, client jobs (overdue in carnelian with "فكّره بلُطف"; tap to mark paid), "جالي شغل · نزّله الخزنة" (keypad sheet). Turn-on flow for a first visit (self-salary + self-payday). Empty state from `04-SCREENS.md`. Reached from Profile ("شغل حر") and Home's ticket source line. Haptics per `06-HAPTICS`.
