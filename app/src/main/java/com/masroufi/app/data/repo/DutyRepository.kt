package com.masroufi.app.data.repo

import androidx.room.withTransaction
import com.masroufi.app.data.db.DutyDao
import com.masroufi.app.data.db.GiftDirection
import com.masroufi.app.data.db.GiftEntity
import com.masroufi.app.data.db.OccasionEntity
import com.masroufi.app.data.db.OccasionOwner
import com.masroufi.app.data.db.PeopleDao
import com.masroufi.app.data.db.PersonEntity
import com.masroufi.app.data.db.TazkaraDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

data class GiftRow(val gift: GiftEntity, val person: PersonEntity?)

/** [receivedTotal] / [givers]: what came in at the occasion and from how many different people ("جالك 14,200 من 38 واحد"). */
data class OccasionView(
    val occasion: OccasionEntity,
    val person: PersonEntity?,
    val gifts: List<GiftRow>,
    val receivedTotal: Long,
    val givenTotal: Long,
    val givers: Int,
)

/** The next occasion of someone else's still ahead: [gaveMe] = what that person gave at the user's own occasion ("X أو أكتر"). */
data class UpcomingDuty(val occasion: OccasionEntity, val person: PersonEntity?, val daysUntil: Long, val gaveMe: Long?)

@Singleton
class DutyRepository @Inject constructor(
    private val db: TazkaraDatabase,
    private val dao: DutyDao,
    private val peopleDao: PeopleDao,
    private val clock: Clock,
) {
    /** Newest occasion first. */
    fun observe(): Flow<List<OccasionView>> = combine(dao.occasions(), dao.gifts(), peopleDao.persons()) { occasions, gifts, persons ->
        val byId = persons.associateBy { it.id }
        occasions.sortedByDescending { it.date }.map { o ->
            val mine = gifts.filter { it.occasionId == o.id }
            val received = mine.filter { it.direction == GiftDirection.RECEIVED }
            OccasionView(
                occasion = o,
                person = o.personId?.let(byId::get),
                gifts = mine.map { GiftRow(it, byId[it.personId]) },
                receivedTotal = received.sumOf { it.amount },
                givenTotal = mine.filter { it.direction == GiftDirection.GIVEN }.sumOf { it.amount },
                givers = received.map { it.personId }.distinct().size,
            )
        }
    }

    suspend fun addOccasion(name: String, date: LocalDate, whose: OccasionOwner, personId: Long? = null): Long {
        require(name.isNotBlank()) { "name must not be blank" }
        return dao.insertOccasion(OccasionEntity(name = name.trim(), date = date, whose = whose, personId = personId))
    }

    suspend fun updateOccasion(occasion: OccasionEntity) {
        require(occasion.name.isNotBlank()) { "name must not be blank" }
        dao.updateOccasion(occasion)
    }

    suspend fun deleteOccasion(id: Long) = db.withTransaction {
        dao.deleteGiftsOf(id)
        dao.deleteOccasion(id)
    }

    suspend fun addGift(occasionId: Long, personId: Long, amount: Long, direction: GiftDirection): Long {
        require(amount > 0) { "amount must be > 0" }
        return dao.insertGift(GiftEntity(occasionId = occasionId, personId = personId, amount = amount, direction = direction))
    }

    suspend fun updateGift(gift: GiftEntity) {
        require(gift.amount > 0) { "amount must be > 0" }
        dao.updateGift(gift)
    }

    suspend fun deleteGift(id: Long) = dao.deleteGift(id)

    /**
     * "X أو أكتر": what [personId] gave the user at the user's own latest occasion they gave at (a person's several gifts
     * at that occasion add up); null when they never gave.
     */
    suspend fun suggestion(personId: Long): Long? {
        val mine = dao.occasionsNow().filter { it.whose == OccasionOwner.MINE }.associateBy { it.id }
        val given = dao.giftsNow().filter { it.personId == personId && it.direction == GiftDirection.RECEIVED && it.occasionId in mine }
        val latest = given.maxByOrNull { mine.getValue(it.occasionId).date.toEpochDay() * 1_000_000 + it.occasionId } ?: return null
        return given.filter { it.occasionId == latest.occasionId }.sumOf { it.amount }
    }

    /** The soonest occasion of someone else's today or later, with the suggestion for its person; null when none. */
    suspend fun upcoming(): UpcomingDuty? {
        val today = LocalDate.now(clock)
        val next = dao.occasionsNow().filter { it.whose == OccasionOwner.THEIRS && !it.date.isBefore(today) }.minByOrNull { it.date } ?: return null
        val person = next.personId?.let { peopleDao.personById(it) }
        return UpcomingDuty(next, person, ChronoUnit.DAYS.between(today, next.date), person?.let { suggestion(it.id) })
    }

    /** Someone else's occasions on [date] (the notification a week ahead), with what that person gave the user. */
    suspend fun theirsOn(date: LocalDate): List<UpcomingDuty> {
        val today = LocalDate.now(clock)
        return dao.occasionsNow().filter { it.whose == OccasionOwner.THEIRS && it.date == date }.map { o ->
            val person = o.personId?.let { peopleDao.personById(it) }
            UpcomingDuty(o, person, ChronoUnit.DAYS.between(today, o.date), person?.let { suggestion(it.id) })
        }
    }

    /** The user's own occasion: what came in and from how many people; null when [occasionId] is not theirs. */
    suspend fun ownTotals(occasionId: Long): Pair<Long, Int>? {
        val o = dao.occasionsNow().firstOrNull { it.id == occasionId && it.whose == OccasionOwner.MINE } ?: return null
        val received = dao.giftsNow().filter { it.occasionId == o.id && it.direction == GiftDirection.RECEIVED }
        return received.sumOf { it.amount } to received.map { it.personId }.distinct().size
    }
}
