package com.masroufi.app.feature.people

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.GameyaPlanEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.repo.FREE_GAMEYAS
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.data.repo.GameyaView
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The gameya screen's sheets: which pocket paid the installment, who to swap with, a plan row to add or edit. */
sealed interface GameyaSheet {
    data object Pay : GameyaSheet
    data object Swap : GameyaSheet
    data class Plan(val editing: GameyaPlanEntity?) : GameyaSheet
}

data class GameyaUi(
    val loading: Boolean = true,
    val gameyas: List<GameyaView> = emptyList(),
    val selectedId: Long? = null,
    val pockets: List<PocketEntity> = emptyList(),
    val plus: Boolean = false,
    val sheet: GameyaSheet? = null,
    val plusSheet: Boolean = false,
) {
    /** The one on show: the picked one, else the oldest. */
    val current: GameyaView? get() = gameyas.firstOrNull { it.gameya.id == selectedId } ?: gameyas.firstOrNull()
    val empty: Boolean get() = !loading && gameyas.isEmpty()
}

/** What the gameya screen and its sheets call on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface GameyaActions {
    fun select(id: Long) {}
    fun openPay() {}
    fun openSwap() {}
    fun openPlan(editing: GameyaPlanEntity? = null) {}
    fun dismissSheet() {}
    fun dismissPlus() {}
    fun requestNew(onGo: () -> Unit) {}
    fun payMine(pocketId: Long) {}
    fun togglePaid(memberId: Long) {}
    fun swapWith(memberId: Long) {}
    fun savePlan(editing: GameyaPlanEntity?, label: String, amount: Long) {}
    fun deletePlan(id: Long) {}
}

private const val TAG = "Gameya"

@HiltViewModel
class GameyaViewModel @Inject constructor(
    private val gameyas: GameyaRepository,
    pocketRepo: PocketRepository,
    private val entitlements: Entitlements,
) : ViewModel(), GameyaActions {
    private val selected = MutableStateFlow<Long?>(null)
    private val sheet = MutableStateFlow<GameyaSheet?>(null)
    private val plusSheet = MutableStateFlow(false)

    private val content = combine(
        gameyas.observe(), pocketRepo.observePockets().map { l -> l.map { it.pocket }.filterNot { it.archived } }, entitlements.isPlus,
    ) { list, pockets, plus -> GameyaUi(loading = false, gameyas = list, pockets = pockets, plus = plus) }

    val state: StateFlow<GameyaUi> = combine(content, selected, sheet, plusSheet) { c, id, s, p ->
        c.copy(selectedId = id, sheet = s, plusSheet = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameyaUi())

    override fun select(id: Long) { selected.value = id }
    override fun openPay() { sheet.value = GameyaSheet.Pay }
    override fun openSwap() { sheet.value = GameyaSheet.Swap }
    override fun openPlan(editing: GameyaPlanEntity?) { sheet.value = GameyaSheet.Plan(editing) }
    override fun dismissSheet() { sheet.value = null }
    override fun dismissPlus() { plusSheet.value = false }

    /** The "+" button: the form, or the Plus card when Free already has its one gameya. */
    override fun requestNew(onGo: () -> Unit) {
        if (!entitlements.isPlus.value && state.value.gameyas.size >= FREE_GAMEYAS) plusSheet.value = true else onGo()
    }

    /** Records the user's installment from [pocketId]; the card then reads "إنت دفعت". */
    override fun payMine(pocketId: Long) = launchSafe("pay") {
        val g = state.value.current ?: return@launchSafe
        sheet.value = null
        gameyas.payMine(g.gameya.id, pocketId)
    }

    /** Ticks or unticks a member's installment for this month (a record, no money). */
    override fun togglePaid(memberId: Long) = launchSafe("toggle paid") {
        val g = state.value.current ?: return@launchSafe
        val paid = g.unpaidMembers.any { it.id == memberId }
        gameyas.setPaid(g.gameya.id, memberId, g.thisMonth, paid)
    }

    override fun swapWith(memberId: Long) = launchSafe("swap") {
        val g = state.value.current ?: return@launchSafe
        sheet.value = null
        gameyas.swapTurn(g.gameya.id, memberId)
    }

    /** Adds a plan row, or saves the edit of [editing]. */
    override fun savePlan(editing: GameyaPlanEntity?, label: String, amount: Long) = launchSafe("save plan") {
        val g = state.value.current ?: return@launchSafe
        sheet.value = null
        if (editing == null) gameyas.addPlanRow(g.gameya.id, label, amount)
        else gameyas.updatePlanRow(editing.copy(label = label.trim(), amount = amount))
    }

    override fun deletePlan(id: Long) = launchSafe("delete plan") {
        sheet.value = null
        gameyas.deletePlanRow(id)
    }

    private fun launchSafe(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "$what failed", e)
            }
        }
    }
}
