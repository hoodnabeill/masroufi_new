package com.masroufi.app.work

import com.masroufi.app.widget.MasroufiWidget
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.masroufi.app.R
import com.masroufi.app.backup.SyncRepository
import com.masroufi.app.backup.SyncStatus
import com.masroufi.app.core.MoneyFormatter
import com.masroufi.app.data.db.AppDatabase
import com.masroufi.app.data.model.GoalEntry
import com.masroufi.app.data.model.TransactionType
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.data.repo.BudgetRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.domain.BudgetStatus
import com.masroufi.app.domain.DebtMath
import com.masroufi.app.domain.DebtStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.concurrent.TimeUnit

@HiltWorker
class DailyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val recurring: RecurringRepository,
    private val budgets: BudgetRepository,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val notifier: Notifier,
    private val clock: Clock,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        recurring.processDue()
        autoSaveGoals()
        if (settings.current().notificationsEnabled) notify()
        MasroufiWidget.refresh(applicationContext)
        return Result.success()
    }

    /**
     * Goals with auto-save get their monthly plan earmarked once a month (never past the target), starting the month
     * after they were made. The entry id is derived from the goal and month, so a rerun cannot save twice.
     */
    private suspend fun autoSaveGoals() {
        val month = YearMonth.now(clock)
        val monthStart = month.atDay(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val done = applicationContext.getSharedPreferences("auto_save", Context.MODE_PRIVATE)
        db.goalDao().observeWithSaved().first().forEach { item ->
            val goal = item.goal
            val plan = goal.plannedMonthlyMinor ?: return@forEach
            if (!goal.autoSave || goal.archived || goal.createdAt >= monthStart) return@forEach
            val key = "${goal.id}:$month"
            if (done.getBoolean(key, false)) return@forEach
            val amount = minOf(plan, goal.targetMinor - item.savedMinor)
            if (amount > 0) {
                val now = clock.millis()
                db.goalDao().upsertEntry(
                    GoalEntry(id = "auto:$key", goalId = goal.id, amountMinor = amount, occurredAt = now, note = applicationContext.getString(R.string.goal_auto_note), updatedAt = now),
                )
            }
            done.edit().putBoolean(key, true).apply()
        }
    }

    private suspend fun notify() {
        val ctx = applicationContext
        val format = MoneyFormatter(Locale.getDefault())
        val today = LocalDate.now(clock)
        val prefs = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        fun once(key: String, block: () -> Unit) {
            if (prefs.getBoolean(key, false)) return
            block()
            prefs.edit().putBoolean(key, true).apply()
        }

        db.recurringDao().active().filter { it.remind && it.nextEpochDay == today.plusDays(1).toEpochDay() }.forEach { rule ->
            once("rec:${rule.id}:${rule.nextEpochDay}") {
                val kind = ctx.getString(if (rule.type == TransactionType.INCOME) R.string.type_income else R.string.type_expense)
                notifier.show(
                    rule.id.hashCode(),
                    ctx.getString(R.string.notify_recurring_title),
                    ctx.getString(R.string.notify_recurring_body, rule.note.ifBlank { kind }, format.format(rule.amountMinor, rule.currency)),
                )
            }
        }

        db.debtDao().observeWithPaid().first().filter { it.debt.remind }.forEach { item ->
            val status = DebtMath.status(item.debt.originalMinor, item.paidMinor, item.debt.dueEpochDay, today)
            val dueSoon = item.debt.dueEpochDay != null && item.debt.dueEpochDay <= today.plusDays(1).toEpochDay()
            if (status != DebtStatus.SETTLED && dueSoon) {
                once("debt:${item.debt.id}:${item.debt.dueEpochDay}") {
                    notifier.show(
                        item.debt.id.hashCode(),
                        ctx.getString(R.string.notify_debt_title),
                        ctx.getString(R.string.notify_debt_body, item.debt.personName, format.format(DebtMath.remaining(item.debt.originalMinor, item.paidMinor), item.debt.currency)),
                    )
                }
            }
        }

        val month = YearMonth.from(today)
        budgets.observeWithProgress().first().forEach { item ->
            when (item.progress.status) {
                BudgetStatus.OVER -> once("budget:${item.budget.id}:$month") {
                    notifier.show(
                        item.budget.id.hashCode(),
                        ctx.getString(R.string.notify_budget_title),
                        ctx.getString(R.string.notify_budget_body, item.budget.name),
                    )
                }
                // "نبّهني لما أوصل 80%": one heads-up a month, before the over-budget one.
                BudgetStatus.WARNING -> once("budget80:${item.budget.id}:$month") {
                    notifier.show(
                        item.budget.id.hashCode(),
                        ctx.getString(R.string.notify_budget_warn_title),
                        ctx.getString(R.string.notify_budget_warn_body, item.budget.name),
                    )
                }
                BudgetStatus.ON_TRACK -> Unit
            }
        }
    }

    companion object {
        private const val NAME = "daily"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyWorker>(12, TimeUnit.HOURS).build(),
            )
        }
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.currentSync().isConfigured) return Result.success()
        return when (sync.sync().status) {
            SyncStatus.NETWORK, SyncStatus.SERVER_ERROR, SyncStatus.REMOTE_CHANGED -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val NAME = "webdav_sync"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
