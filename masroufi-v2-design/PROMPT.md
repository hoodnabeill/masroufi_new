# Prompt for Claude Code — Masroufi v2 redesign

Paste everything below the line into Claude Code, from the root of the Masroufi repo, after copying the `masroufi-v2-design/` folder into the repo root.

---

You're working on Masroufi (مصروفي, package `com.masroufi.app`), an existing, working Android app: Arabic-first (RTL, Egyptian dialect), offline-first, privacy-focused personal finance for Egypt, default currency EGP.

I've added a folder `masroufi-v2-design/` with the full v2 redesign:
- `README.md` — design system, the new navigation, every screen and feature with its behaviour and formulas. Read it fully first.
- `screens/*.dc.html` — one HTML mockup per screen. They are the visual source of truth: exact colours, spacing, radii, font sizes, copy and interaction logic (see the `<script>` block at the bottom of each file). Treat them as reference to rebuild natively, NOT code to embed in a WebView.

## How I want you to work

1. **Explore before changing anything.** Read the codebase and tell me in a short summary: UI toolkit (Compose or XML Views), architecture (MVVM, etc.), data layer (Room/DataStore/etc.), navigation, DI, existing theme/colours/typography files, and which v2 features already exist in some form (wallets, budgets, goals, recurring, gameya, debts, auto-split, backup, lock, gamification). Then map each design screen to the existing screen/file it replaces, or mark it NEW.
2. **Propose a phased plan and wait for my OK** before writing code. Use the phases below unless the codebase suggests a better order.
3. **Work one phase at a time.** After each phase: build (`./gradlew assembleDebug`), run existing tests, fix what you broke, then give me a short summary of what changed and what to check on the device. Commit each phase separately with a clear message.
4. **Don't break existing data.** Any schema change needs a proper Room migration (no destructive fallback). Existing users must keep all their transactions.
5. **Keep the app offline-first.** No new network calls except where the README says a feature is premium/online. No analytics or tracking SDKs.
6. Ask me when something in the design conflicts with how the app currently works — don't silently pick.

## Phases

**Phase 0 — Fix the CI build.** GitHub Actions fails on missing `@color/splashscreen_background` and `@drawable/splashscreen_logo` referenced from `res/drawable/ic_launcher_background.xml`. Add `splashscreen_background = #0B0A10` and create `splashscreen_logo` as a vector drawable of the new pocket glyph (spec in README → App icon). Check `.gitignore` isn't excluding resources.

**Phase 1 — Design system.** Replace the purple-glass theme with "Midnight Ledger" tokens from the README: colours, typography (Readex Pro for display/numbers, IBM Plex Sans Arabic for body — bundle the fonts, don't load from the network), radii, spacing. One central theme file; remove hardcoded colours. Add a money formatter: `1,105 ج.م` — no trailing dot, decimals only when non-zero, currency rendered smaller/lighter. Full RTL check.

**Phase 2 — Navigation + Home.** New bottom nav: 4 tabs (الرئيسية، الحركة، الخطة، المزيد) + raised centre add button. Rebuild Home per `Main.dc.html`: safe-to-spend hero ("تقدر تصرف النهارده"), privacy eye toggle, streak/ritual card with week dots, type-to-log input + quick chips with last amount, smart insights, stacked-bar category breakdown, recent transactions grouped by day.

**Phase 3 — Logging loop.** Quick-add bottom sheet (`AddSheet.dc.html`) with custom keypad, type switch (صرف/دخل/تحويل/سلفة), live "% of today's number" feedback. Reward screen after saving (`Reward.dc.html`). Points, levels, streaks, streak freeze, badges (`Achievements.dc.html`). Activity screen with daily totals and today's empty-state CTA.

**Phase 4 — Plan tab.** Budgets with smart suggested plan, new-budget flow (amount-first, live daily translation, no currency field), goals tab with "reach it faster" boost, new-goal flow (templates, live preview, auto-save toggle).

**Phase 5 — Money tools.** Wallets (+ paste-an-SMS parser, local only), recurring bills & instalments with reminders, gameya tracker, debts with WhatsApp nudge (open `wa.me` intent with prefilled text; never send automatically), auto-split income rules.

**Phase 6 — Insights & recap.** Financial health score, end-of-month forecast chart, monthly recap story with share image (option to hide numbers). "اسأل مصروفي" stays behind a premium flag and can be stubbed for now.

**Phase 7 — Onboarding + icon.** 8-step onboarding (`Onboarding.dc.html`) that ends with the user's first log; new adaptive launcher icon (foreground/background layers + monochrome themed icon) per README.

Start with step 1 (explore + summary + plan). Don't write code until I approve the plan.
