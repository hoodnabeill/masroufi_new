package com.masroufi.app.data.repo

import com.masroufi.app.core.toLocalDate
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.domain.PayCycle
import com.masroufi.app.domain.Progress
import com.masroufi.app.domain.ProgressInput
import com.masroufi.app.domain.ProgressSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Streak, level and badges, recomputed from live data whenever it changes (see [Progress] for why nothing is stored). */
@Singleton
class ProgressRepository @Inject constructor(
    private val transactions: TransactionRepository,
    private val goals: GoalRepository,
    private val recurring: RecurringRepository,
    private val gameyas: GameyaRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    fun observe(): Flow<ProgressSnapshot> = combine(
        transactions.observeLogTimes(),
        goals.observeGoals(),
        recurring.observeAll(),
        gameyas.observeAll(),
        settings.settings,
    ) { times, g, rules, gameyaList, s ->
        val days = times.mapTo(HashSet()) { it.toLocalDate(clock.zone) }
        Progress.snapshot(
            ProgressInput(
                logDays = days,
                entries = times.size,
                goals = g.size,
                goalsDone = g.count { it.goal.targetMinor > 0 && it.savedMinor >= it.goal.targetMinor },
                subscriptions = rules.count { it.serviceKey != null },
                gameyas = gameyaList.size,
                paydaySet = PayCycle.isSet(s.paydayDay),
            ),
            LocalDate.now(clock),
        )
    }
}
