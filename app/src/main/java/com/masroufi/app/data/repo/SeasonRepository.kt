package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.core.ceilDiv
import com.masroufi.app.data.db.JarDao
import com.masroufi.app.data.db.Pot
import com.masroufi.app.data.db.SeasonDao
import com.masroufi.app.data.db.SeasonEntity
import com.masroufi.app.data.db.SeasonItemEntity
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.data.db.TazkaraDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.MonthDay
import java.time.YearMonth
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class SeasonView(
    val season: SeasonEntity,
    val date: LocalDate,
    /** Hijri seasons: the real day depends on moon sighting ("تقريبًا"). */
    val approximate: Boolean,
    val daysUntil: Long,
    /** Jar money earmarked for the season. */
    val saved: Long,
    val perDayNeeded: Long,
    val items: List<SeasonItemEntity>,
)

data class TogetherView(val neededPerMonth: Long, val usualPerMonth: Long, val gap: Long)

/**
 * The season's next day on or after [today]. Hijri seasons convert via [HijrahDate] (day clamped to the month's
 * length); summer and school repeat every year on their month and day; a personal occasion is its own date.
 */
internal fun nextOccurrence(season: SeasonEntity, today: LocalDate): LocalDate {
    val month = season.hijriMonth
    if (month != null) {
        val year = HijrahDate.from(today).get(ChronoField.YEAR)
        return (year..year + 1).map { y ->
            val day = minOf(season.hijriDay ?: 1, HijrahDate.of(y, month, 1).lengthOfMonth())
            LocalDate.from(HijrahDate.of(y, month, day))
        }.first { !it.isBefore(today) }
    }
    val date = checkNotNull(season.gregorianDate) { "season ${season.id} has no date" }
    if (season.kind == SeasonKind.PERSONAL) return date
    val thisYear = MonthDay.from(date).atYear(today.year)
    return if (thisYear.isBefore(today)) MonthDay.from(date).atYear(today.year + 1) else thisYear
}

/** Per day needed = (target - saved) / days until, rounded up; 0 when met. Today itself counts as one day. */
internal fun perDayNeeded(target: Long, saved: Long, daysUntil: Long): Long =
    if (saved >= target) 0L else (target - saved).ceilDiv(maxOf(1L, daysUntil))

@Singleton
class SeasonRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val seasonDao: SeasonDao,
    private val jarDao: JarDao,
    private val clock: Clock,
) {
    /** Every season, soonest first; the defaults are seeded on first use. */
    fun observe(): Flow<List<SeasonView>> = flow {
        seedDefaults()
        val today = LocalDate.now(clock)
        emitAll(
            combine(seasonDao.all(), seasonDao.items(), jarDao.rows(Pot.JAR)) { seasons, items, rows ->
                seasons.map { s ->
                    val date = nextOccurrence(s, today)
                    val days = ChronoUnit.DAYS.between(today, date).coerceAtLeast(0)
                    val saved = rows.earmarkedFor(seasonId = s.id)
                    SeasonView(
                        season = s, date = date, approximate = s.hijriMonth != null, daysUntil = days, saved = saved,
                        perDayNeeded = perDayNeeded(s.target, saved, days), items = items.filter { it.seasonId == s.id },
                    )
                }.sortedBy { it.date }
            },
        )
    }

    /**
     * "All seasons together": Σ over unmet seasons of ceil((target - saved) / months until) against the average
     * monthly jar growth over the last 3 calendar months (this month excluded). The gap is never hidden.
     */
    suspend fun together(): TogetherView {
        seedDefaults()
        val today = LocalDate.now(clock)
        val rows = jarDao.rowsNow(Pot.JAR)
        val needed = seasonDao.allNow().sumOf { s ->
            val saved = rows.earmarkedFor(seasonId = s.id)
            if (s.target <= saved) 0L else {
                val months = ChronoUnit.MONTHS.between(today, nextOccurrence(s, today)).coerceAtLeast(1)
                (s.target - saved).ceilDiv(months)
            }
        }
        val thisMonth = YearMonth.from(today).atDay(1)
        val usual = Math.floorDiv(rows.growth(thisMonth.minusMonths(3), thisMonth, clock.zone), 3L)
        return TogetherView(needed, usual, (needed - usual).coerceAtLeast(0))
    }

    suspend fun createPersonal(name: String, date: LocalDate, target: Long): Long {
        require(target >= 0) { "target must be >= 0" }
        val color = CATEGORY_COLORS[(seasonDao.count()) % CATEGORY_COLORS.size]
        return seasonDao.insert(
            SeasonEntity(kind = SeasonKind.PERSONAL, name = name, gregorianDate = date, target = target, color = color, updatedAt = clock.millis()),
        )
    }

    /** Edits a season (target, name, date) as typed by the user. */
    suspend fun update(season: SeasonEntity) {
        require(season.target >= 0) { "target must be >= 0" }
        seasonDao.update(season.copy(updatedAt = clock.millis()))
    }

    suspend fun addItem(seasonId: Long, label: String, amount: Long): Long {
        require(amount >= 0) { "amount must be >= 0" }
        return seasonDao.insertItem(SeasonItemEntity(seasonId = seasonId, label = label, amount = amount))
    }

    suspend fun updateItem(item: SeasonItemEntity) {
        require(item.amount >= 0) { "amount must be >= 0" }
        seasonDao.updateItem(item)
    }

    suspend fun deleteItem(id: Long) = seasonDao.deleteItem(id)

    /** Ramadan, the two Eids (Hijri), summer July 1 and school September 15 (Gregorian); targets 0 for the user to type. */
    private suspend fun seedDefaults() {
        db.withTransaction {
            if (seasonDao.count() > 0) return@withTransaction
            val now = clock.millis()
            val year = LocalDate.now(clock).year
            listOf(
                SeasonEntity(kind = SeasonKind.RAMADAN, name = "رمضان", hijriMonth = 9, hijriDay = 1, target = 0, color = 0),
                SeasonEntity(kind = SeasonKind.EID_AL_FITR, name = "العيد الصغير", hijriMonth = 10, hijriDay = 1, target = 0, color = 0),
                SeasonEntity(kind = SeasonKind.EID_AL_ADHA, name = "العيد الكبير", hijriMonth = 12, hijriDay = 10, target = 0, color = 0),
                SeasonEntity(kind = SeasonKind.SUMMER, name = "المصيف", gregorianDate = LocalDate.of(year, 7, 1), target = 0, color = 0),
                SeasonEntity(kind = SeasonKind.SCHOOL, name = "دخول المدارس", gregorianDate = LocalDate.of(year, 9, 15), target = 0, color = 0),
            ).forEachIndexed { i, s -> seasonDao.insert(s.copy(color = CATEGORY_COLORS[i % CATEGORY_COLORS.size], updatedAt = now)) }
        }
    }
}
