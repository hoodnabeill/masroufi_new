# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Egyptians managing everyday money on their phone. In priority order (the first wins any trade-off):

1. **Salaried employees** paid once a month (often around the 25th or the 1st) who need the salary to last until the next one: rent, bills, transport, groceries.
2. **Household managers** running the home budget: groceries, school fees, family spending, gameya.
3. **Students and young adults** on an allowance or first income, mostly cash plus mobile wallets.
4. **Freelancers and cash workers** with irregular income who need to know what is really left.

## Product Purpose

Masroufi (مصروفي) answers, at a glance and in the user's own words, "how much do I have, and how much can I spend until payday?", and makes logging a spend faster than forgetting it. Success is a user who logs daily spending in a couple of taps and never runs out of money before the salary arrives.

## Positioning

Built around how Egyptians actually handle money, not a translated Western budgeting app: cash first, mobile wallets (Vodafone Cash, InstaPay, Fawry) and bank cards side by side, salary-to-salary cycles instead of calendar months, gameya (جمعية) and family lending (سلف) as first-class features, EGP by default, Arabic first. Offline, no account, nothing leaves the phone unless the user exports or syncs it.

## Operating Context

- Spending is logged on the go, one-handed, often right after paying cash at a shop, kiosk, microbus or delivery door.
- Money lives in several places at once: cash in the pocket, a mobile wallet, a bank card; transfers between them are common.
- The month runs from payday to payday.
- Gameya rounds and money lent to or borrowed from family and friends are tracked by person.

## Capabilities and Constraints

- Offline-first Android app (Kotlin, Jetpack Compose, Room). Built and tested only on GitHub Actions; no device run in the build loop.
- Arabic (Egyptian colloquial, عامية) is the primary UI language; English is fully supported. RTL throughout; numbers stay left-to-right.
- Money is stored as integer minor units, EGP by default, multi-currency without summing across currencies.
- Features: wallets, income/expense/transfer, budgets, goals, recurring items, gameya, debts and lending, auto-split, analytics, encrypted backup and WebDAV sync, PIN/biometric lock.
- Billing does not exist; everything is unlocked during early access and the app says so.
- Wallet provider names (Vodafone Cash, InstaPay, Fawry) may appear as plain text labels only; the app has no integration with those services and must not imply one.

## Brand Commitments

- Name: Masroufi / مصروفي.
- Voice: Arabic copy is friendly Egyptian colloquial, like a sensible friend talking about money ("صرفت إيه النهارده؟", "فاضلك كام؟"). Never preachy or shaming about spending. Security, backup and legal text stays precise and unambiguous.

## Evidence on Hand

No users, testimonials, reviews, statistics or press exist. Do not invent any.

## Product Principles

1. **Two taps to log.** The most common spend (cash, everyday category, round amount) must take the fewest possible steps.
2. **Payday is the clock.** "Days until salary" and "safe to spend per day" matter more than calendar-month totals.
3. **Where the money is, as people say it.** Cash, wallets and cards are named the way Egyptians name them.
4. **Calm, never judgmental.** Facts and gentle nudges, no guilt.
5. **Private by default.** Nothing leaves the device without an explicit user action.

## Accessibility & Inclusion

Arabic RTL as a first-class layout, large font scales, TalkBack, a "Solid surfaces" option for users who find translucency hard to read, and the system "remove animations" setting.
