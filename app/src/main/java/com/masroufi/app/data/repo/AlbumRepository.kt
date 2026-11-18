package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.ProverbDao
import com.masroufi.app.data.db.StampDao
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.GoalKind
import com.masroufi.app.domain.LEVEL_NAMES
import com.masroufi.app.domain.MatchDay
import com.masroufi.app.domain.Proverb
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.daysUnder
import com.masroufi.app.domain.level
import com.masroufi.app.domain.longestStreak
import com.masroufi.app.domain.matchDay
import com.masroufi.app.domain.nextLevelAt
import com.masroufi.app.domain.protectedDays
import com.masroufi.app.domain.stampCount
import com.masroufi.app.domain.streak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CollectedProverb(val proverb: Proverb, val collectedOn: LocalDate)

data class StampView(val kind: StampKind, val earnedOn: LocalDate, val count: Int)

/** [nextLevelAt]/[ticketsToNext] are null at the top level. Streaks and [daysUnder] count days closed by hand. */
data class AlbumView(
    val level: Int,
    val levelName: String,
    val handClosed: Int,
    val nextLevelAt: Int?,
    val ticketsToNext: Int?,
    val streak: Int,
    /** Streak days the safety ticket (Plus) kept: the streak with it minus without; 0 on Free. */
    val protectedDays: Int = 0,
    val longestStreak: Int,
    val daysUnder: Int,
    val proverbs: List<CollectedProverb>,
    val lockedProverbs: Int,
    val stamps: List<StampView>,
    /** Newest first; closed days only. */
    val stubs: List<MatchDay>,
)

const val RECENT_STUBS = 14

@Singleton
class AlbumRepository @Inject constructor(
    private val ticketDao: DayTicketDao,
    private val proverbDao: ProverbDao,
    private val stampDao: StampDao,
    private val clock: Clock,
    private val plus: Entitlements,
) {
    fun observe(stubDays: Int = RECENT_STUBS): Flow<AlbumView> =
        combine(ticketDao.all(), proverbDao.all(), stampDao.all(), plus.isPlus) { rows, collected, stamps, isPlus ->
            val today = LocalDate.now(clock)
            val days = rows.map { DayResult(it.date, it.status, it.amount, it.spent) }
            val hand = days.count { it.status == TicketStatus.CLOSED_BY_HAND }
            val lvl = level(hand)
            val next = nextLevelAt(hand)
            val byNumber = collected.associateBy { it.number }
            val cards = ProverbCatalog.all.filter { it.number in byNumber }
                .map { CollectedProverb(it, byNumber.getValue(it.number).collectedOn) }
            val byDate = days.associateBy { it.date }
            AlbumView(
                level = lvl, levelName = LEVEL_NAMES[lvl - 1], handClosed = hand,
                nextLevelAt = next, ticketsToNext = next?.let { it - hand },
                streak = streak(days, today, isPlus), protectedDays = if (isPlus) protectedDays(days, today) else 0, longestStreak = longestStreak(days), daysUnder = daysUnder(days),
                proverbs = cards, lockedProverbs = ProverbCatalog.all.size - cards.size,
                stamps = stamps.sortedBy { it.earnedOn }.map { StampView(it.kind, it.earnedOn, stampCount(it.kind, days)) },
                stubs = (0L until stubDays).map { today.minusDays(it) }.map { matchDay(it, byDate[it]) }
                    .filter { it.goal != GoalKind.NONE },
            )
        }
}
