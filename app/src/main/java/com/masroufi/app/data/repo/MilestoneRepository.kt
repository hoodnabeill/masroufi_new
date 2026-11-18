package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.data.db.StampDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.domain.LEVEL_NAMES
import com.masroufi.app.domain.StampKind
import com.masroufi.app.domain.level
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Something the UI celebrates once: a new level (2 and up) or a stamp. [key] is what `markSeen` stores. */
sealed class Milestone(val key: String) {
    data class NewLevel(val level: Int, val name: String) : Milestone("level:$level")
    data class NewStamp(val kind: StampKind) : Milestone("stamp:${kind.name}")
}

@Singleton
class MilestoneRepository @Inject constructor(
    private val ticketDao: DayTicketDao,
    private val stampDao: StampDao,
    private val settings: SettingsStore,
) {
    /** Milestones not yet seen, levels first; empty once each was marked. */
    fun unseen(): Flow<List<Milestone>> = combine(ticketDao.handClosedCount(), stampDao.all(), settings.settings) { hand, stamps, s ->
        val lvl = level(hand)
        val levels = if (lvl >= 2) listOf(Milestone.NewLevel(lvl, LEVEL_NAMES[lvl - 1])) else emptyList()
        (levels + stamps.sortedBy { it.earnedOn }.map { Milestone.NewStamp(it.kind) }).filter { it.key !in s.milestonesSeen }
    }

    suspend fun markSeen(milestone: Milestone) = settings.markMilestoneSeen(milestone.key)
}
