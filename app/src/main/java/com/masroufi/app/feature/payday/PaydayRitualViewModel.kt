package com.masroufi.app.feature.payday

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.EntryDao
import com.masroufi.app.data.db.PocketDao
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.DebtWithPerson
import com.masroufi.app.data.repo.JarRepository
import com.masroufi.app.data.repo.JarShare
import com.masroufi.app.data.repo.PaydayRitualRepository
import com.masroufi.app.data.repo.RitualView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RitualUi(
    val loading: Boolean = true,
    val salary: Long = 0,
    val pocketName: String = "",
    val share: JarShare = JarShare.Percent(DEFAULT_SHARE),
    val jarTotal: Long = 0,
    val view: RitualView? = null,
    /** Step 2: what the user owes people (loans); the step shows only when there is any. */
    val debts: List<DebtWithPerson> = emptyList(),
    /** The debts ticked to be repaid in full when the tickets print. */
    val repay: Set<Long> = emptySet(),
    val pocketId: Long? = null,
    val amountSheet: Boolean = false,
    /** Printed or skipped: the screen goes Home. */
    val finished: Boolean = false,
    val printed: Boolean = false,
)

const val DEFAULT_SHARE = 10
private const val TAG = "PaydayRitual"

/** يوم القبض for the income [entryId]: every number is the repository's preview of the chosen share. */
@HiltViewModel
class PaydayRitualViewModel @Inject constructor(
    private val ritual: PaydayRitualRepository,
    private val jar: JarRepository,
    private val entryDao: EntryDao,
    private val pocketDao: PocketDao,
    private val settings: SettingsStore,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val entryId: Long = savedState.get<Long>("entryId") ?: 0L
    private val _state = MutableStateFlow(RitualUi())
    val state: StateFlow<RitualUi> = _state.asStateFlow()
    private var preview: Job? = null
    private var applying = false

    init {
        viewModelScope.launch {
            try {
                val entry = entryDao.byId(entryId)
                if (entry == null) {
                    _state.update { it.copy(loading = false, finished = true) }
                    return@launch
                }
                val share = JarShare.Percent(settings.current().lastJarSharePercent ?: DEFAULT_SHARE)
                val debts = ritual.debtsIOwe()
                _state.update {
                    it.copy(
                        loading = false, salary = entry.amount, share = share, debts = debts, repay = debts.map { d -> d.debt.id }.toSet(), pocketId = entry.pocketId,
                        pocketName = pocketDao.byId(entry.pocketId)?.name.orEmpty(), jarTotal = jar.observe().first().total,
                    )
                }
                refresh()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "load failed", e)
                _state.update { it.copy(loading = false, finished = true) }
            }
        }
    }

    private fun refresh() {
        preview?.cancel()
        preview = viewModelScope.launch {
            val s = _state.value
            val v = ritual.preview(s.salary, s.share, s.repay)
            _state.update { if (it.share == s.share && it.repay == s.repay) it.copy(view = v) else it }
        }
    }

    fun setPercent(percent: Int) = setShare(JarShare.Percent(percent))

    fun setAmount(piastres: Long) {
        setShare(JarShare.Amount(piastres))
        _state.update { it.copy(amountSheet = false) }
    }

    private fun setShare(share: JarShare) {
        _state.update { it.copy(share = share) }
        refresh()
    }

    /** Tick or untick a debt of step 2; the preview recomputes what is left for the tickets. */
    fun toggleDebt(id: Long) {
        _state.update { it.copy(repay = if (id in it.repay) it.repay - id else it.repay + id) }
        refresh()
    }

    fun openAmount() = _state.update { it.copy(amountSheet = true) }
    fun dismissAmount() = _state.update { it.copy(amountSheet = false) }

    /** "اطبع تذاكر الشهر": once, however many taps. */
    fun print() {
        val s = _state.value
        if (applying || s.view == null) return
        applying = true
        viewModelScope.launch {
            try {
                ritual.apply(s.salary, s.share, entryId, s.repay, s.pocketId)
                _state.update { it.copy(finished = true, printed = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "apply failed", e)
                applying = false
            }
        }
    }

    /** Nothing is forced: close without applying, and the income is not offered again. */
    fun skip() {
        if (applying) return
        applying = true
        viewModelScope.launch {
            try {
                ritual.markOffered(entryId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "skip failed", e)
            }
            _state.update { it.copy(finished = true) }
        }
    }
}
