package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.GoalDao
import com.masroufi.app.data.db.GoalEntity
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.JarMovementEntity
import com.masroufi.app.data.db.JarSource
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.TazkaraDatabase
import com.masroufi.app.data.prefs.Settings
import com.masroufi.app.data.prefs.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** A withdrawal or earmark above the free (unearmarked) jar total; [free] is what is available. */
class AboveFreeJar(val free: Long) : Exception("above the free jar total ($free)")

data class JarSources(val leftoverCount: Int, val leftoverSum: Long, val roundUpSum: Long, val manualSum: Long)

data class GoalView(val goal: GoalEntity, val earmarked: Long)

/** Grams in hundredths (123 = 1.23 g); [leftToFirstGram] only while the jar is under one gram. */
data class GoldView(val pricePerGram: Long, val priceDate: LocalDate?, val gramsHundredths: Long, val leftToFirstGram: Long?)

data class JarView(
    val total: Long,
    val monthGrowth: Long,
    val sources: JarSources,
    val goals: List<GoalView>,
    val free: Long,
    /** Null until the user types a gold price. */
    val gold: GoldView?,
    /** When the oldest movement happened (epoch millis); null for an untouched jar. */
    val firstAt: Long? = null,
)

/**
 * Earmarks (goals and seasons, decision 3) are stored as a pair of MANUAL jar rows: +x carrying the goalId or seasonId
 * and -x carrying neither. The jar total is unchanged, the earmarked sum of a goal or season is the sum of its rows,
 * and free = total - every earmark row. A release is the reverse pair (-x on the goal, +x free).
 */
internal fun JarMovementEntity.isEarmark(): Boolean = goalId != null || seasonId != null

internal fun List<JarMovementEntity>.earmarkedFor(goalId: Long? = null, seasonId: Long? = null): Long =
    filter { (goalId != null && it.goalId == goalId) || (seasonId != null && it.seasonId == seasonId) }.sumOf { it.amount }

internal fun List<JarMovementEntity>.free(): Long = sumOf { it.amount } - filter { it.isEarmark() }.sumOf { it.amount }

/** Σ movements with `at` in [from, to) local days. */
internal fun List<JarMovementEntity>.growth(from: LocalDate, to: LocalDate, zone: ZoneId): Long {
    val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = to.atStartOfDay(zone).toInstant().toEpochMilli()
    return filter { it.at in start until end }.sumOf { it.amount }
}

/**
 * Manual money put in: positive MANUAL/CHANGED_MIND rows outside earmarks, minus the +x halves of releases (each equal
 * to a negative earmark row), so earmark pairs never count as money put in.
 */
internal fun List<JarMovementEntity>.sources(): JarSources {
    val leftovers = filter { it.source == JarSource.TICKET_LEFTOVER }
    val manualIn = filter { (it.source == JarSource.MANUAL || it.source == JarSource.CHANGED_MIND) && !it.isEarmark() && it.amount > 0 }
        .sumOf { it.amount }
    val releases = filter { it.isEarmark() && it.amount < 0 }.sumOf { -it.amount }
    return JarSources(
        leftoverCount = leftovers.size,
        leftoverSum = leftovers.sumOf { it.amount },
        roundUpSum = filter { it.source == JarSource.ROUND_UP }.sumOf { it.amount },
        manualSum = manualIn - releases,
    )
}

internal fun gold(total: Long, s: Settings): GoldView? {
    val price = s.goldPricePerGram ?: return null
    val held = total.coerceAtLeast(0)
    return GoldView(
        pricePerGram = price,
        priceDate = s.goldPriceDate?.let(LocalDate::ofEpochDay),
        gramsHundredths = held * 100 / price,
        leftToFirstGram = if (held < price) price - held else null,
    )
}

@Singleton
class JarRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val jarDao: JarDao,
    private val goalDao: GoalDao,
    private val settings: SettingsStore,
    private val clock: Clock,
) {
    fun observe(): Flow<JarView> = combine(jarDao.rows(Pot.JAR), goalDao.all(), settings.settings) { rows, goals, s ->
        val total = rows.sumOf { it.amount }
        val month = YearMonth.now(clock)
        JarView(
            total = total,
            monthGrowth = rows.growth(month.atDay(1), month.plusMonths(1).atDay(1), clock.zone),
            sources = rows.sources(),
            goals = goals.map { GoalView(it, rows.earmarkedFor(goalId = it.id)) },
            free = rows.free(),
            gold = gold(total, s),
            firstAt = rows.minOfOrNull { it.at },
        )
    }

    /** A MANUAL deposit: a share of the user's own money (the jar is not a pocket, so no pocket changes). */
    suspend fun deposit(amount: Long) {
        require(amount > 0) { "amount must be > 0" }
        jarDao.insert(manual(amount))
    }

    /** A MANUAL withdrawal, at most the free total ([AboveFreeJar] otherwise). */
    suspend fun withdraw(amount: Long) {
        require(amount > 0) { "amount must be > 0" }
        db.withTransaction {
            val free = jarDao.rowsNow(Pot.JAR).free()
            if (amount > free) throw AboveFreeJar(free)
            jarDao.insert(manual(-amount))
        }
    }

    suspend fun createGoal(name: String, target: Long, date: LocalDate? = null): Long {
        require(target > 0) { "target must be > 0" }
        return goalDao.insert(GoalEntity(name = name, target = target, date = date, updatedAt = clock.millis()))
    }

    suspend fun updateGoal(goal: GoalEntity) {
        require(goal.target > 0) { "target must be > 0" }
        goalDao.update(goal.copy(updatedAt = clock.millis()))
    }

    /** Releases whatever is earmarked for the goal back to the free jar, then deletes it. */
    suspend fun deleteGoal(id: Long) {
        db.withTransaction {
            val held = jarDao.rowsNow(Pot.JAR).earmarkedFor(goalId = id)
            if (held > 0) insertPair(-held, goalId = id, seasonId = null)
            goalDao.delete(id)
        }
    }

    /** Earmarks [amount] of the free jar for one goal or one season ([AboveFreeJar] above the free total). */
    suspend fun earmark(amount: Long, goalId: Long? = null, seasonId: Long? = null) {
        require(amount > 0) { "amount must be > 0" }
        require((goalId == null) != (seasonId == null)) { "earmark for exactly one goal or season" }
        db.withTransaction {
            val free = jarDao.rowsNow(Pot.JAR).free()
            if (amount > free) throw AboveFreeJar(free)
            insertPair(amount, goalId, seasonId)
        }
    }

    /** Gives back up to what is earmarked for the goal or season. */
    suspend fun release(amount: Long, goalId: Long? = null, seasonId: Long? = null) {
        require(amount > 0) { "amount must be > 0" }
        require((goalId == null) != (seasonId == null)) { "release from exactly one goal or season" }
        db.withTransaction {
            val held = jarDao.rowsNow(Pot.JAR).earmarkedFor(goalId, seasonId)
            require(amount <= held) { "cannot release more than is earmarked ($held)" }
            insertPair(-amount, goalId, seasonId)
        }
    }

    /** Caller holds the transaction. */
    private suspend fun insertPair(amount: Long, goalId: Long?, seasonId: Long?) {
        jarDao.insert(manual(amount).copy(goalId = goalId, seasonId = seasonId))
        jarDao.insert(manual(-amount))
    }

    private fun manual(amount: Long): JarMovementEntity {
        val now = clock.millis()
        return JarMovementEntity(pot = Pot.JAR, amount = amount, source = JarSource.MANUAL, at = now, updatedAt = now)
    }
}
