package com.masroufi.app.data.repo

import com.masroufi.app.data.db.DayTicketDao
import com.masroufi.app.domain.DayResult
import com.masroufi.app.domain.MatchView
import com.masroufi.app.domain.buildMatch
import com.masroufi.app.entitlements.Entitlements
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The weekly match (Saturday-Friday); the rules live in `buildMatch`. History beyond 4 weeks is Plus: see `lockedWeeks`. */
@Singleton
class MatchRepository @Inject constructor(
    private val ticketDao: DayTicketDao,
    private val entitlements: Entitlements,
    private val clock: Clock,
) {
    fun observe(): Flow<MatchView> = combine(ticketDao.all(), entitlements.isPlus) { rows, plus ->
        buildMatch(rows.map { DayResult(it.date, it.status, it.amount, it.spent) }, LocalDate.now(clock), plus)
    }
}
