package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.DutyDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.EnvelopeDao
import com.masroufi.app.data.db.GameyaDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.KidsDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.SeasonDao
import com.masroufi.app.data.db.TrackedItemDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.protectedDays
import com.masroufi.app.domain.streakResult
import com.masroufi.app.entitlements.TrialState
import com.masroufi.app.entitlements.trialInfo
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Every place the app asks for Plus. */
enum class PlusFeature {
    GOLD, ROUND_UPS, SEASONS, ZAKAT, DUTY, BASKET, ENVELOPES, GAMEYA, KIDS, MATCH_HISTORY, INSTALLMENT_COMPARE, SAFETY_TICKET,
}

/**
 * One paywall row: only numbers and the user's own names; the UI words them. [value] is the headline number (always
 * above zero in a built list); [usage] is how much the user used the feature in the usage window (see [LossRowsRepository]).
 */
sealed interface LossRow {
    val feature: PlusFeature
    val value: Long
    val usage: Int

    /** [value] = grams in hundredths (37 = 0.37 g). */
    data class Gold(override val value: Long, val pricePerGram: Long, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.GOLD
    }

    /** [value] = piastres collected this month by round-ups. */
    data class RoundUps(override val value: Long, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.ROUND_UPS
    }

    /** [value] = seasons planned; [nearestSaved] is what is set aside for the nearest planned one. */
    data class Seasons(
        override val value: Long, val nearestName: String, val nearestSaved: Long, val nearestDaysUntil: Long, override val usage: Int,
    ) : LossRow {
        override val feature get() = PlusFeature.SEASONS
    }

    /** [value] = distinct people in the ledger; the next occasion today or later, if any. */
    data class Duty(override val value: Long, val nextName: String?, val nextDaysUntil: Long?, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.DUTY
    }

    /** [value] = streak days the safety ticket kept. */
    data class SafetyTicket(override val value: Long, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.SAFETY_TICKET
    }

    /** [value] = tracked items in the basket. */
    data class Basket(override val value: Long, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.BASKET
    }

    /** [value] = active envelopes beyond the four free ones. */
    data class Envelopes(override val value: Long, override val usage: Int) : LossRow {
        override val feature get() = PlusFeature.ENVELOPES
    }
}

/**
 * What a Plus feature would do with the user's data (the paywall opened from that feature). [amount] is piastres and
 * [count] a number of things, by feature: GOLD amount = the jar (+ [gramsHundredths] at [pricePerGram] when a gold
 * price is typed); ZAKAT amount = the jar; ROUND_UPS amount = this month's round-ups; SEASONS = seasons planned;
 * DUTY = people in the ledger; BASKET = tracked items; ENVELOPES = active envelopes; GAMEYA = active gameyas;
 * KIDS = children; MATCH_HISTORY = days closed by hand; INSTALLMENT_COMPARE = installment plans; SAFETY_TICKET = days it
 * would have kept. Null = nothing to say (the UI uses the feature's plain line).
 */
data class FeaturePitch(
    val feature: PlusFeature,
    val amount: Long? = null,
    val count: Int? = null,
    val gramsHundredths: Long? = null,
    val pricePerGram: Long? = null,
)

/** [rows]: at most five; empty means the plain version with [plainFeatures]. [first] is set when opened from a feature. */
data class LossRows(val rows: List<LossRow>, val first: FeaturePitch?, val plainFeatures: List<PlusFeature>) {
    val plain: Boolean get() = rows.isEmpty()
}

const val MAX_LOSS_ROWS = 5

/** The three features the plain version names. */
val PLAIN_FEATURES = listOf(PlusFeature.GOLD, PlusFeature.SEASONS, PlusFeature.DUTY)

/** The tie order when usage is equal: the order of 09-PREMIUM's examples. */
private val ROW_ORDER = listOf(
    PlusFeature.GOLD, PlusFeature.ROUND_UPS, PlusFeature.SEASONS, PlusFeature.DUTY, PlusFeature.SAFETY_TICKET,
    PlusFeature.BASKET, PlusFeature.ENVELOPES,
)

/** Only rows above zero, [first]'s row on top, then most used first (ties in [ROW_ORDER]), at most [MAX_LOSS_ROWS]. */
fun orderLossRows(candidates: List<LossRow>, first: PlusFeature? = null): List<LossRow> =
    candidates.filter { it.value > 0 }
        .sortedWith(
            compareByDescending<LossRow> { it.feature == first }
                .thenByDescending { it.usage }
                .thenBy { ROW_ORDER.indexOf(it.feature) },
        )
        .take(MAX_LOSS_ROWS)

/**
 * The paywall's rows from real data. Usage window: the trial's days (start through today or its end); with no trial
 * (a free user who tapped a Plus feature) the last 14 days. Usage per row kind (events in the window):
 * GOLD = jar deposits (not earmarks) + 1 if the gold price was typed in it; ROUND_UPS = round-up jar rows;
 * SEASONS = planned seasons edited in it + season earmarks put in it; DUTY = occasions dated in it + gifts on them
 * (gifts and occasions store no creation time); SAFETY_TICKET = missed days it bridged in it; BASKET = priced entries
 * of listed items in it; ENVELOPES = envelopes beyond four edited in it + moves touching them. A season counts as
 * planned only with a target or money set aside (the five seeded seasons start empty).
 */
@Singleton
class LossRowsRepository @Inject constructor(
    private val jarDao: JarDao,
    private val seasonDao: SeasonDao,
    private val dutyDao: DutyDao,
    private val trackedDao: TrackedItemDao,
    private val entryDao: EntryDao,
    private val envelopeDao: EnvelopeDao,
    private val ticketDao: DayTicketDao,
    private val gameyaDao: GameyaDao,
    private val kidsDao: KidsDao,
    private val recurringDao: RecurringDao,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    suspend fun build(first: PlusFeature? = null): LossRows {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val trial = trialInfo(s, today)
        val trialStart = trial.startDay.takeIf { trial.state == TrialState.ACTIVE || trial.state == TrialState.ENDED }
        val from = trialStart ?: today.minusDays(13)
        val to = minOf(today, trial.endDay ?: today).plusDays(1)
        val fromMs = from.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val toMs = to.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        fun inWindow(ms: Long) = ms in fromMs until toMs
        fun inWindowDay(d: LocalDate) = !d.isBefore(from) && d.isBefore(to)

        val jar = jarDao.rowsNow(Pot.JAR)
        val total = jar.sumOf { it.amount }
        val goldView = gold(total, s)
        val month = YearMonth.from(today)
        val roundUps = jar.filter { it.source == JarSource.ROUND_UP }
        val roundUpsMonth = roundUps.growth(month.atDay(1), month.plusMonths(1).atDay(1), clock.zone)

        val seasons = seasonDao.allNow().map { it to jar.earmarkedFor(seasonId = it.id) }.filter { (season, saved) -> season.target > 0 || saved > 0 }
        val nearest = seasons.minByOrNull { nextOccurrence(it.first, today) }
        val occasions = dutyDao.occasionsNow()
        val gifts = dutyDao.giftsNow()
        val names = (gifts.map { it.personId } + occasions.mapNotNull { it.personId }).toSet().size
        val nextOccasion = occasions.filter { !it.date.isBefore(today) }.minByOrNull { it.date }
        val days = ticketDao.allNow().map { DayResult(it.date, it.status, it.amount, it.spent) }
        val safety = protectedDays(days, today)
        val bridged = streakResult(days, today, plusSafety = true).bridged
        val priced = entryDao.pricedEntries().groupBy { it.trackedItemId }
        val listed = trackedDao.allNow().filter { it.addedByUser || (priced[it.id]?.size ?: 0) >= 2 }
        val activeEnvelopes = envelopeDao.allNow().filter { !it.archived }.sortedBy { it.id }
        val extraEnvelopes = activeEnvelopes.drop(FREE_ENVELOPES)
        val extraIds = extraEnvelopes.map { it.id }.toSet()

        val goldUsage = jar.count { it.amount > 0 && !it.isEarmark() && inWindow(it.at) } +
            if (s.goldPriceDate?.let(LocalDate::ofEpochDay)?.let { inWindowDay(it) } == true) 1 else 0
        val candidates = listOfNotNull(
            goldView?.takeIf { it.gramsHundredths > 0 }?.let { LossRow.Gold(it.gramsHundredths, it.pricePerGram, goldUsage) },
            LossRow.RoundUps(roundUpsMonth, roundUps.count { inWindow(it.at) }),
            nearest?.let { (n, saved) ->
                LossRow.Seasons(
                    seasons.size.toLong(), n.name, saved, ChronoUnit.DAYS.between(today, nextOccurrence(n, today)).coerceAtLeast(0),
                    seasons.count { (season, _) -> inWindow(season.updatedAt) } + jar.count { r -> r.seasonId != null && r.amount > 0 && inWindow(r.at) },
                )
            },
            LossRow.Duty(
                names.toLong(), nextOccasion?.name, nextOccasion?.let { ChronoUnit.DAYS.between(today, it.date) },
                occasions.count { inWindowDay(it.date) } + gifts.count { g -> occasions.any { it.id == g.occasionId && inWindowDay(it.date) } },
            ),
            LossRow.SafetyTicket(safety.toLong(), bridged.count { inWindowDay(it) }),
            LossRow.Basket(listed.size.toLong(), listed.sumOf { i -> priced[i.id].orEmpty().count { inWindow(it.happenedAt) } }),
            LossRow.Envelopes(
                extraEnvelopes.size.toLong(),
                extraEnvelopes.count { inWindow(it.updatedAt) } +
                    envelopeDao.movesNow().count { (it.fromId in extraIds || it.toId in extraIds) && inWindow(it.at) },
            ),
        )
        val pitch = first?.let {
            FeaturePitch(
                feature = it,
                amount = when (it) {
                    PlusFeature.GOLD, PlusFeature.ZAKAT -> total
                    PlusFeature.ROUND_UPS -> roundUpsMonth
                    else -> null
                }?.takeIf { v -> v > 0 },
                count = when (it) {
                    PlusFeature.SEASONS -> seasons.size
                    PlusFeature.DUTY -> names
                    PlusFeature.BASKET -> listed.size
                    PlusFeature.ENVELOPES -> activeEnvelopes.size
                    PlusFeature.GAMEYA -> gameyaDao.activeCount()
                    PlusFeature.KIDS -> kidsDao.childrenNow().size
                    PlusFeature.MATCH_HISTORY -> days.count { d -> d.status == TicketStatus.CLOSED_BY_HAND }
                    PlusFeature.INSTALLMENT_COMPARE -> recurringDao.allNow().count { r -> r.kind == RecurringKind.INSTALLMENT }
                    PlusFeature.SAFETY_TICKET -> safety
                    else -> null
                }?.takeIf { v -> v > 0 },
                gramsHundredths = if (it == PlusFeature.GOLD) goldView?.gramsHundredths else null,
                pricePerGram = if (it == PlusFeature.GOLD) goldView?.pricePerGram else null,
            )
        }
        return LossRows(orderLossRows(candidates, first), pitch, PLAIN_FEATURES)
    }
}
