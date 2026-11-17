package com.masroufi.app.feature.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.EntryRepository
import com.masroufi.app.data.repo.HomeData
import com.masroufi.app.data.repo.HomeRepository
import com.masroufi.app.data.repo.NOTE_MAX_CHARS
import com.masroufi.app.data.repo.NewEntry
import com.masroufi.app.data.repo.RepeatChip
import com.masroufi.app.data.repo.TicketRepository
import com.masroufi.app.data.repo.punchesTicket
import com.masroufi.app.di.ProcessLifecycle
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.domain.parseQuickLog
import com.masroufi.app.domain.projectedAtPayday
import com.masroufi.app.domain.weekStart
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.FeedbackBus
import com.masroufi.app.feature.add.Pending
import com.masroufi.app.feature.add.PendingPunches
import com.masroufi.app.feature.add.PunchFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/** The ticket's line under the number (Task 17 ruling 5 and 7). */
sealed interface SubLine {
    data object None : SubLine
    data class Spent(val ticket: Long, val spent: Long, val count: Int) : SubLine
    /** [spread]: the second sentence ("spread over the other days"), left out on the cycle's last day. */
    data class Over(val over: Long, val spread: Boolean) : SubLine
    /** End-of-month row whose pool is short of what must be paid by [short]. */
    data class Short(val short: Long) : SubLine
}

data class TicketUi(val amount: Long, val spent: Long, val holes: Int, val subLine: SubLine) {
    val left: Long get() = amount - spent
}

/** After closing: tomorrow's ticket and the card earned today. */
data class TomorrowUi(val amount: Long, val proverbNumber: Int?)

enum class StubKind { Under, Over, Today, Empty }

/** [amount]: the leftover (Under) or the overspend (Over), piastres. */
data class WeekStub(val kind: StubKind, val amount: Long, val isToday: Boolean)

/** [projected] is null when the line is hidden (negative, or an end-of-month row). */
data class CycleUi(val daysLeft: Int, val progress: Float, val spendable: Long, val projected: Long?, val paydayDay: Int)

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Error : HomeUiState
    data class Ready(
        val today: LocalDate,
        val greeting: Greeting,
        val streak: Int,
        /** Null once today is closed: [tomorrow] shows instead. */
        val ticket: TicketUi?,
        val tomorrow: TomorrowUi?,
        val repeats: List<RepeatChip>,
        val week: List<WeekStub>,
        val closedUnder: Int,
        val jarTotal: Long,
        val cycle: CycleUi,
        val collected: Int,
        /** First open of this day's ticket: Home plays the new-ticket moment, then calls markTicketShown. */
        val newTicket: Boolean = false,
    ) : HomeUiState
}

private const val TAG = "Home"
private const val DOUBLE_TAP_MS = 400L
private const val PENDING_CATCH_UP_MS = 2_000L
private const val FEEDBACK_MS = 3_000L

@HiltViewModel
class HomeViewModel @Inject constructor(
    homeRepo: HomeRepository,
    private val ticketRepo: TicketRepository,
    private val entryRepo: EntryRepository,
    private val categoryDao: CategoryDao,
    private val pocketDao: PocketDao,
    private val pendingPunches: PendingPunches,
    @ProcessLifecycle private val processLifecycle: Lifecycle,
    private val clock: Clock,
    feedbackBus: FeedbackBus,
    private val settings: SettingsStore,
) : ViewModel() {
    private val failure = MutableStateFlow<Throwable?>(null)
    private val _saveFailed = MutableStateFlow(false)
    /** The last punch failed and was rolled back ("ماتسجّلتش. جرّب تاني."); cleared by the next punch. */
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    private val _feedback = MutableStateFlow<PunchFeedback.Saved?>(null)
    /** The add sheet's last punch, shown under the ticket for 3 s. */
    val feedback: StateFlow<PunchFeedback.Saved?> = _feedback.asStateFlow()
    private var feedbackJob: Job? = null

    private val prefills = Channel<AddPrefill>(Channel.BUFFERED)
    /** The quick log could not log on its own: open the add sheet with this. */
    val prefill: Flow<AddPrefill> = prefills.receiveAsFlow()

    /** The quick log field's text. */
    var quickText by mutableStateOf("")

    private val lastTap = HashMap<String, Long>()

    // One log re-emits several upstreams one by one; conflate keeps only the newest.
    private val home: StateFlow<HomeData?> = homeRepo.observeHome()
        .conflate()
        .catch { fail(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<HomeUiState> = combine(home, pendingPunches.pending, failure) { d, pending, f ->
        val row = d?.today
        when {
            f != null -> HomeUiState.Error
            d == null || row == null -> HomeUiState.Loading
            else -> {
                // The add sheet's placeholders stop counting once this row holds them; then they can go.
                pendingPunches.prune(row.entryCount)
                homeUi(d, pending.filter { it.expectedHoles > row.entryCount }, LocalTime.now(clock).hour)
            }
        }
    }
        .conflate()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private val onStart = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = print()
    }

    init {
        print()
        processLifecycle.addObserver(onStart)
        viewModelScope.launch {
            feedbackBus.events.collect(::show)
        }
        viewModelScope.launch {
            while (true) {
                val midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
                delay(midnight - clock.millis() + 1_000)
                printNow()
            }
        }
    }

    override fun onCleared() {
        processLifecycle.removeObserver(onStart)
    }

    /** The one place a punch's outcome reaches the screen: the add sheet's events and Home's own saves both land here. */
    private fun show(f: PunchFeedback) {
        when (f) {
            PunchFeedback.Failed -> {
                feedbackJob?.cancel()
                _feedback.value = null
                _saveFailed.value = true
            }
            is PunchFeedback.Saved -> {
                _saveFailed.value = false
                feedbackJob?.cancel()
                feedbackJob = viewModelScope.launch {
                    _feedback.value = f
                    try {
                        delay(FEEDBACK_MS)
                    } finally {
                        if (_feedback.value === f) _feedback.value = null
                    }
                }
            }
        }
    }

    /** The new-ticket moment for [epochDay] has played: it does not play again today. */
    fun markTicketShown(epochDay: Long) {
        viewModelScope.launch {
            try {
                settings.setLastTicketShown(epochDay)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "lastTicketShown not saved", e)
            }
        }
    }

    private fun print() {
        viewModelScope.launch { printNow() }
    }

    private suspend fun printNow() {
        try {
            ticketRepo.printToday()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        }
    }

    private fun fail(e: Throwable) {
        Log.e(TAG, "home failed", e)
        failure.value = e
    }

    fun punchRepeat(chip: RepeatChip) {
        if (doubleTap("chip:${chip.categoryId}:${chip.amount}:${chip.pocketId}")) return
        viewModelScope.launch {
            punch(NewEntry(EntryKind.SPENT, chip.amount, chip.pocketId, categoryId = chip.categoryId, happenedAt = clock.millis()))
        }
    }

    /** IME Done / mic result: logs straight away when the text names an amount and a category, else hands it to the sheet. */
    fun submitQuickLog() {
        val text = quickText.trim()
        if (text.isEmpty() || doubleTap("quick")) return
        viewModelScope.launch {
            val categories = categoryDao.all().first()
            val parsed = parseQuickLog(text, categories.map { it.name })
            val category = parsed?.categoryName?.let { name -> categories.firstOrNull { it.name == name } }
            val pocket = pocketDao.all().first().filter { it.punchesTicket }.minByOrNull { it.id }
            if (parsed != null && category != null && pocket != null) {
                val entry = NewEntry(EntryKind.SPENT, parsed.amount, pocket.id, categoryId = category.id, happenedAt = clock.millis())
                // On failure the text stays so nothing typed is lost.
                if (punch(entry) && quickText.trim() == text) quickText = ""
            } else {
                prefills.send(AddPrefill(parsed?.amount, category?.id, text.take(NOTE_MAX_CHARS)))
                if (quickText.trim() == text) quickText = ""
            }
        }
    }

    /** True for a second tap on the same action within 400 ms. */
    private fun doubleTap(key: String): Boolean {
        val now = clock.millis()
        val last = lastTap[key]
        if (last != null && now - last < DOUBLE_TAP_MS) return true
        lastTap[key] = now
        return false
    }

    /** Shows the hole at once; rolls it back if storage refuses. */
    private suspend fun punch(entry: NewEntry): Boolean {
        _saveFailed.value = false
        val row = home.value?.today
        val holesBefore = (row?.entryCount ?: 0) + pendingPunches.pending.value.size
        val expected = holesBefore + 1
        val left = row?.takeIf { it.status == TicketStatus.OPEN }
            ?.let { it.amount - it.spent - pendingPunches.pending.value.sumOf { p -> p.punched } - entry.amount }
        val id = pendingPunches.add(Pending(0, entry.amount))
        return try {
            entryRepo.log(entry)
            if (left != null) show(PunchFeedback.Saved(left, firstOfDay = holesBefore == 0))
            // Drop the placeholder only once the stored row has the hole, so the count never dips and re-punches.
            withTimeoutOrNull(PENDING_CATCH_UP_MS) { home.first { (it?.today?.entryCount ?: 0) >= expected } }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "log failed", e)
            show(PunchFeedback.Failed)
            false
        } finally {
            pendingPunches.remove(id)
        }
    }
}

/** Builds the screen from the stored data plus the punches not stored yet. Caller checks d.today != null. */
internal fun homeUi(d: HomeData, pending: List<Pending>, hour: Int): HomeUiState.Ready {
    val row = checkNotNull(d.today)
    val today = row.date
    val daysLeft = d.cycle.daysLeft(today)
    val pendingSum = pending.sumOf { it.punched }
    val spent = row.spent + pendingSum
    val holes = row.entryCount + pending.size
    val open = row.status == TicketStatus.OPEN
    val spendable = d.spendable - pendingSum

    val subLine = when {
        row.endOfMonth -> (row.eomPool ?: 0L).let { if (it < 0) SubLine.Short(-it) else SubLine.None }
        holes == 0 -> SubLine.None
        spent > row.amount -> SubLine.Over(spent - row.amount, spread = daysLeft > 1)
        else -> SubLine.Spent(row.amount, spent, holes)
    }

    val tomorrow = if (open) null else {
        val carry = if (row.destination == LeftoverDestination.TOMORROW) row.leftover else 0L
        val amount = d.tomorrow.preview(spendable, d.jarTotal, d.emergencyTotal, carry)
        TomorrowUi(amount, row.proverbNumber)
    }

    val start = weekStart(today)
    val week = (0L..6L).map { i ->
        val date = start.plusDays(i)
        val r = d.week.firstOrNull { it.date == date }
        val isToday = date == today
        when {
            r != null && r.status != TicketStatus.OPEN ->
                if (r.spent > r.amount) WeekStub(StubKind.Over, r.spent - r.amount, isToday) else WeekStub(StubKind.Under, r.leftover, isToday)
            isToday -> WeekStub(StubKind.Today, 0, true)
            else -> WeekStub(StubKind.Empty, 0, false)
        }
    }

    val total = ChronoUnit.DAYS.between(d.cycle.start, d.cycle.nextPayday).toInt().coerceAtLeast(1)
    val projected = if (row.endOfMonth) null else {
        projectedAtPayday(d.spendable + row.spent, d.unpaidBills, row.base, row.carriedIn, daysLeft).takeIf { it >= 0 }
    }

    return HomeUiState.Ready(
        today = today,
        greeting = greetingFor(today, hour, d.cycle),
        streak = d.streak,
        ticket = if (open) TicketUi(row.amount, spent, holes, subLine) else null,
        tomorrow = tomorrow,
        repeats = d.repeats,
        week = week,
        closedUnder = d.week.count { it.status != TicketStatus.OPEN && it.spent <= it.amount },
        jarTotal = d.jarTotal,
        cycle = CycleUi(
            daysLeft = daysLeft,
            progress = ((total - daysLeft).toFloat() / total).coerceIn(0f, 1f),
            spendable = spendable,
            projected = projected,
            paydayDay = d.cycle.nextPayday.dayOfMonth,
        ),
        collected = d.collected,
        newTicket = open && d.lastTicketShown != today.toEpochDay(),
    )
}
