package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.RecurringDao
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.data.db.WishDao
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.BuyContext
import com.masroufi.app.domain.averageMonthlyIncome
import com.masroufi.app.feature.add.AddPrefill
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

const val WISH_HOURS = 48L
private const val HOUR_MS = 3_600_000L

/** [hoursLeft] rounds up and is 0 once the 48 hours have passed; [due] = passed and not answered yet. */
data class WishView(val wish: WishEntity, val hoursLeft: Long, val due: Boolean)

private val minuteTicks: Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(60_000)
    }
}

@Singleton
class WishRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val wishDao: WishDao,
    private val entryDao: EntryDao,
    private val pocketDao: PocketDao,
    private val recurringDao: RecurringDao,
    private val jarDao: JarDao,
    private val ticketDao: DayTicketDao,
    private val jar: JarRepository,
    private val seasons: SeasonRepository,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    /** Parks something for 48 hours from now; the installment is what the shop said, as typed. */
    suspend fun park(name: String, price: Long, installmentMonthly: Long? = null, installmentMonths: Int? = null): Long {
        require(name.isNotBlank()) { "a wish needs a name" }
        require(price > 0) { "price must be > 0" }
        return wishDao.insert(
            WishEntity(
                name = name.trim(), price = price, parkedAt = clock.millis(),
                installmentMonthly = installmentMonthly, installmentMonths = installmentMonths,
            ),
        )
    }

    /** Waiting first, then the answered; hours left are re-read every minute so a row falls due while the screen is open. */
    fun observe(): Flow<List<WishView>> = combine(wishDao.all(), minuteTicks) { list, _ ->
        val now = clock.millis()
        list.map { w ->
            val left = w.parkedAt + WISH_HOURS * HOUR_MS - now
            WishView(w, hoursLeft = if (left <= 0) 0 else (left + HOUR_MS - 1) / HOUR_MS, due = w.answer == WishAnswer.WAITING && left <= 0)
        }
    }

    /** Home's question: 48 hours passed and no answer yet. */
    fun dueForAnswer(): Flow<List<WishEntity>> = observe().map { list -> list.filter { it.due }.map { it.wish } }

    /** Price of everything the user decided not to buy. */
    fun totalChangedMind(): Flow<Long> = wishDao.changedMindTotal()

    /**
     * "صرفت نظر": with [toJar], min(price, spendable) goes to the jar as a MANUAL deposit (never more than what is
     * spendable). Returns what moved; answering twice moves nothing.
     */
    suspend fun changedMind(id: Long, toJar: Boolean): Long = db.withTransaction {
        val wish = checkNotNull(wishDao.byId(id)) { "no wish $id" }
        if (wish.answer != WishAnswer.WAITING) return@withTransaction 0L
        val moved = if (toJar) minOf(wish.price, spendable()) else 0L
        wishDao.update(wish.copy(answer = WishAnswer.CHANGED_MIND, answeredAt = clock.millis(), movedToJar = moved.takeIf { it > 0 }))
        if (moved > 0) jar.deposit(moved)
        moved
    }

    /** What "صرفت نظر" would move to the jar right now: the price, but never more than is spendable; 0 = nothing to ask. */
    suspend fun jarOffer(id: Long): Long {
        val wish = wishDao.byId(id)?.takeIf { it.answer == WishAnswer.WAITING } ?: return 0
        return minOf(wish.price, spendable())
    }

    /**
     * "اشتريتها": the add sheet's prefill (amount, name as the note, [AddPrefill.wishId]). Nothing is marked here: the
     * add sheet calls [markBought] once the entry is saved, so closing it unsaved leaves the wish waiting.
     */
    suspend fun bought(id: Long): AddPrefill {
        val wish = checkNotNull(wishDao.byId(id)) { "no wish $id" }
        return AddPrefill(amount = wish.price, categoryId = null, note = wish.name, wishId = wish.id)
    }

    /** The entry for the wish was saved. Answering twice changes nothing. */
    suspend fun markBought(id: Long) {
        val wish = wishDao.byId(id) ?: return
        if (wish.answer == WishAnswer.WAITING) wishDao.update(wish.copy(answer = WishAnswer.BOUGHT, answeredAt = clock.millis()))
    }

    /** What is free to spend: counting pockets - unpaid bills before payday - jar - emergency (as the ticket reserves them), never below 0. */
    suspend fun spendable(): Long {
        val s = settings.current()
        val cycle = entryDao.payCycleOn(LocalDate.now(clock), s.payday, clock)
        val reserved = recurringDao.unpaidDueBefore(cycle.nextPayday) +
            jarDao.totalNow(Pot.JAR).coerceAtLeast(0) + jarDao.totalNow(Pot.EMERGENCY).coerceAtLeast(0)
        return (pocketDao.countingBalanceTotal() - reserved).coerceAtLeast(0)
    }

    /** The numbers "دي تساوي" turns a price into; unknowns stay null. */
    suspend fun context(): BuyContext {
        val s = settings.current()
        val today = LocalDate.now(clock)
        val ticket = ticketDao.forDate(today)?.takeIf { !it.endOfMonth && it.amount > 0 }?.amount
        val thisMonth = YearMonth.from(today)
        val months = (1L..3L).map { thisMonth.minusMonths(it) }.map { m ->
            entryDao.receivedBetween(
                m.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
                m.plusMonths(1).atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli(),
            )
        }
        val season = seasons.observe().first().firstOrNull()?.takeIf { it.saved > 0 }
        return BuyContext(
            ticket = ticket,
            monthlyIncome = averageMonthlyIncome(months, s.usualSalary),
            goldPricePerGram = s.goldPricePerGram,
            seasonSaved = season?.saved,
            seasonName = season?.season?.name,
        )
    }
}
