package com.masroufi.app.domain

import com.masroufi.app.core.PIASTRES

/** A number in tenths (73 = 7.3), kept as a Long so nothing rounds twice. */
data class Tenths(val value: Long) {
    /** "8" when whole, else one decimal ("7.3"). */
    fun plain(): String = if (value % 10 == 0L) "${value / 10}" else "${value / 10}.${value % 10}"

    /** The spoken form: x.5 is "x ونص" (0.5 is "نص"), a whole number is plain, anything else one decimal. */
    fun spoken(): String = if (value % 10 == 5L) (if (value < 10) "نص" else "${value / 10} ونص") else plain()
}

private fun roundedDiv(n: Long, d: Long): Long = (2 * n + d) / (2 * d) // half up, n >= 0, d > 0

/** Price ÷ today's ticket to one decimal; null without a ticket. */
fun ticketsFor(price: Long, ticket: Long?): Tenths? = ticket?.takeIf { it > 0 && price > 0 }?.let { Tenths(roundedDiv(price * 10, it)) }

/** Whole days of tickets the price takes, rounded up ("تاخد 8 أيام من تذاكرك"). */
fun cashDays(price: Long, ticket: Long?): Int? =
    ticket?.takeIf { it > 0 && price > 0 }?.let { ((price + it - 1) / it).toInt() }

/** Average monthly income of the last 3 complete months, else the usual salary; null when neither is known. */
fun averageMonthlyIncome(lastThreeMonths: List<Long>, usualSalary: Long?): Long? {
    val total = lastThreeMonths.sum()
    return if (total > 0) total / 3 else usualSalary?.takeIf { it > 0 }
}

/** Price ÷ (monthly income ÷ 30), one decimal; null when the income is unknown. */
fun workDaysFor(price: Long, monthlyIncome: Long?): Tenths? =
    monthlyIncome?.takeIf { it > 0 && price > 0 }?.let { Tenths(roundedDiv(price * 10 * 30, it)) }

/** Grams of gold in hundredths (36 = 0.36 g); null without a typed gold price. */
fun goldGramsHundredths(price: Long, goldPricePerGram: Long?): Long? =
    goldPricePerGram?.takeIf { it > 0 && price > 0 }?.let { roundedDiv(price * 100, it) }

/** Share of what is saved for the nearest season, in percent; null when nothing is saved. */
fun seasonSharePercent(price: Long, saved: Long?): Int? =
    saved?.takeIf { it > 0 && price > 0 }?.let { roundedDiv(price * 100, it).toInt() }

/** What the shop said, as typed: [total] = monthly × months, [extra] = total - cash price, [perDay] = monthly ÷ 30 (piastres) for [months]. */
data class Installment(val total: Long, val extra: Long, val perDay: Long, val months: Int)

fun installment(cashPrice: Long, monthly: Long, months: Int): Installment? {
    if (cashPrice <= 0 || monthly <= 0 || months <= 0) return null
    val total = monthly * months
    return Installment(total, total - cashPrice, roundedDiv(monthly, 30), months)
}

/** What the screen has to know about the user; every unknown stays null and its tile is hidden. */
data class BuyContext(
    val ticket: Long?,
    val monthlyIncome: Long?,
    val goldPricePerGram: Long?,
    val seasonSaved: Long?,
    val seasonName: String?,
)

data class BeforeBuy(
    val price: Long,
    val tickets: Tenths?,
    val workDays: Tenths?,
    val goldGramsHundredths: Long?,
    val seasonSharePercent: Int?,
    val seasonName: String?,
    /** Days of tickets paying cash takes. */
    val cashDays: Int?,
)

fun beforeBuy(price: Long, ctx: BuyContext): BeforeBuy = BeforeBuy(
    price = price,
    tickets = ticketsFor(price, ctx.ticket),
    workDays = workDaysFor(price, ctx.monthlyIncome),
    goldGramsHundredths = goldGramsHundredths(price, ctx.goldPricePerGram),
    seasonSharePercent = seasonSharePercent(price, ctx.seasonSaved),
    seasonName = ctx.seasonName,
    cashDays = cashDays(price, ctx.ticket),
)

/** Two decimals: 36 -> "0.36". */
fun gramsText(hundredths: Long): String = "${hundredths / PIASTRES}.${(hundredths % PIASTRES).toString().padStart(2, '0')}"
