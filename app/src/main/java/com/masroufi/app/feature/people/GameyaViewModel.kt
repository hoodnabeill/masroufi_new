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

private const val TAG = "Gameya"

@HiltViewModel
class GameyaViewModel @Inject constructor(
    private val gameyas: GameyaRepository,
    pocketRepo: PocketRepository,
    private val entitlements: Entitlements,
) : ViewModel() {
    private val selected = MutableStateFlow<Long?>(null)
    private val sheet = MutableStateFlow<GameyaSheet?>(null)
    private val plusSheet = MutableStateFlow(false)

    private val content = combine(
        gameyas.observe(), pocketRepo.observePockets().map { l -> l.map { it.pocket }.filterNot { it.archived } }, entitlements.isPlus,
    ) { list, pockets, plus -> GameyaUi(loading = false, gameyas = list, pockets = pockets, plus = plus) }

    val state: StateFlow<GameyaUi> = combine(content, selected, sheet, plusSheet) { c, id, s, p ->
        c.copy(selectedId = id, sheet = s, plusSheet = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GameyaUi())

    fun select(id: Long) { selected.value = id }
    fun openPay() { sheet.value = GameyaSheet.Pay }
    fun openSwap() { sheet.value = GameyaSheet.Swap }
    fun openPlan(editing: GameyaPlanEntity? = null) { sheet.value = GameyaSheet.Plan(editing) }
    fun dismissSheet() { sheet.value = null }
    fun dismissPlus() { plusSheet.value = false }

    /** The "+" button: the form, or the Plus card when Free already has its one gameya. */
    fun requestNew(onGo: () -> Unit) {
        if (!entitlements.isPlus.value && state.value.gameyas.size >= FREE_GAMEYAS) plusSheet.value = true else onGo()
    }

    /** Records the user's installment from [pocketId]; the card then reads "إنت دفعت". */
    fun payMine(pocketId: Long) = launchSafe("pay") {
        val g = state.value.current ?: return@launchSafe
        gameyas.payMine(g.gameya.id, pocketId)
        sheet.value = null
    }

    /** Ticks or unticks a member's installment for this month (a record, no money). */
    fun togglePaid(memberId: Long) = launchSafe("toggle paid") {
        val g = state.value.current ?: return@launchSafe
        val paid = g.unpaidMembers.any { it.id == memberId }
        gameyas.setPaid(g.gameya.id, memberId, g.thisMonth, paid)
    }

    fun swapWith(memberId: Long) = launchSafe("swap") {
        val g = state.value.current ?: return@launchSafe
        gameyas.swapTurn(g.gameya.id, memberId)
        sheet.value = null
    }

    /** Adds a plan row, or saves the edit of [editing]. */
    fun savePlan(editing: GameyaPlanEntity?, label: String, amount: Long) = launchSafe("save plan") {
        val g = state.value.current ?: return@launchSafe
        if (editing == null) gameyas.addPlanRow(g.gameya.id, label, amount)
        else gameyas.updatePlanRow(editing.copy(label = label.trim(), amount = amount))
        sheet.value = null
    }

    fun deletePlan(id: Long) = launchSafe("delete plan") {
        gameyas.deletePlanRow(id)
        sheet.value = null
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
