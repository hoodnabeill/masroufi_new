package com.masroufi.app.feature.installments

import com.masroufi.app.feature.roll.PendingDeletes
import com.masroufi.app.feature.roll.PendingDelete
import com.masroufi.app.feature.roll.DeleteKind
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.InstallmentRepository
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val NAME_MAX = 30
const val MAX_INSTALLMENTS = 120
private const val DEFAULT_TOTAL = 12
private const val TAG = "InstallmentForm"

data class InstallmentForm(
    val loaded: Boolean = false,
    val name: String = "",
    val amount: AmountInput = AmountInput(),
    val day: Int = 1,
    /** How many payments in all; null = no end. */
    val total: Int? = DEFAULT_TOTAL,
    /** Payments made before the app counted them. */
    val paidBefore: Int = 0,
    val pocketId: Long? = null,
    val pockets: List<PocketEntity> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = loaded && !saving && name.isNotBlank() && amount.toPiastres() > 0 && pocketId != null && (total == null || paidBefore <= total)
}

/** قسط جديد ("id" 0) and عدّل القسط (the id): one question per block. */
@HiltViewModel
class InstallmentFormViewModel @Inject constructor(
    private val installments: InstallmentRepository,
    private val recurring: RecurringRepository,
    pocketRepo: PocketRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
    private val deletes: PendingDeletes,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _form = MutableStateFlow(InstallmentForm())
    val form: StateFlow<InstallmentForm> = _form.asStateFlow()
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()

    val isEdit: Boolean get() = id != 0L

    init {
        viewModelScope.launch {
            val pockets = pocketRepo.observePockets().first().map { it.pocket }.filter { it.kind != PocketKind.VAULT }
            val item = if (id != 0L) recurring.get(id) else null
            _form.update {
                if (item == null) {
                    it.copy(loaded = true, pockets = pockets, pocketId = pockets.firstOrNull()?.id, day = LocalDate.now(clock).dayOfMonth)
                } else {
                    it.copy(
                        loaded = true, pockets = pockets, name = item.name, amount = AmountInput.of(item.amount), day = item.scheduleDay,
                        total = item.totalCount, paidBefore = item.paidBefore, pocketId = item.pocketId,
                    )
                }
            }
        }
    }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun setDay(day: Int) = _form.update { it.copy(day = day.coerceIn(1, 31)) }
    fun setPocket(id: Long) = _form.update { it.copy(pocketId = id) }
    fun digit(d: Int) = _form.update { it.copy(amount = it.amount.digit(d)) }
    fun point() = _form.update { it.copy(amount = it.amount.point()) }
    fun backspace() = _form.update { it.copy(amount = it.amount.backspace()) }

    /** "مفتوح" = no total: the plan counts nothing down and is not part of the freedom date. */
    fun setOpen(open: Boolean) = _form.update { it.copy(total = if (open) null else (it.total ?: DEFAULT_TOTAL), paidBefore = if (open) 0 else it.paidBefore) }

    /** The total never goes under what was already paid. */
    fun stepTotal(delta: Int) = _form.update { f -> f.total?.let { f.copy(total = (it + delta).coerceIn(maxOf(1, f.paidBefore), MAX_INSTALLMENTS)) } ?: f }
    fun stepPaid(delta: Int) = _form.update { f -> f.copy(paidBefore = (f.paidBefore + delta).coerceIn(0, f.total ?: 0)) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val monthly = f.amount.toPiastres()
                val pocket = checkNotNull(f.pocketId)
                if (isEdit) installments.edit(id, f.name, monthly, f.day, pocket, f.total, f.paidBefore)
                else installments.add(f.name, monthly, f.day, pocket, f.total, f.paidBefore)
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _form.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    fun delete() {
        if (!isEdit) return
        val id = id
        deletes.schedule(PendingDelete(id, kind = DeleteKind.INSTALLMENT)) { installments.delete(id) }
        _gone.value = true
    }
}
