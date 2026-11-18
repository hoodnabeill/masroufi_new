package com.masroufi.app.feature.kids

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.ChildEntity
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.data.repo.KidsRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val NAME_MAX = 20
private const val LABEL_MAX = 24
private const val DEFAULT_AGE = 8
private const val TAG = "ChildForm"

/** The four amounts of the form, each with its own keypad. */
enum class ChildAmount { WEEKLY, TARGET, PER, ADD }

data class ChildForm(
    val loaded: Boolean = false,
    val name: String = "",
    val birthYear: Int = 0,
    val colorIndex: Int = 0,
    val weekly: AmountInput = AmountInput(),
    val jar: Boolean = false,
    val jarLabel: String = "",
    val jarTarget: AmountInput = AmountInput(),
    val match: Boolean = false,
    val matchPer: AmountInput = AmountInput(),
    val matchAdd: AmountInput = AmountInput(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean
        get() = loaded && !saving && name.isNotBlank() && weekly.toPiastres() > 0 &&
            (!match || (matchPer.toPiastres() > 0 && matchAdd.toPiastres() > 0))
}

/** طفل جديد ("id" 0) and عدّل الطفل: one question per block. */
@HiltViewModel
class ChildFormViewModel @Inject constructor(
    private val kids: KidsRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private val _form = MutableStateFlow(ChildForm())
    val form: StateFlow<ChildForm> = _form.asStateFlow()
    private val _gone = MutableStateFlow(false)
    val gone: StateFlow<Boolean> = _gone.asStateFlow()
    private var original: ChildEntity? = null

    val isEdit: Boolean get() = id != 0L
    val thisYear: Int get() = LocalDate.now(clock).year

    init {
        viewModelScope.launch {
            val c = if (id != 0L) kids.view().children.firstOrNull { it.child.id == id }?.child else null
            original = c
            _form.update {
                if (c == null) {
                    it.copy(loaded = true, birthYear = thisYear - DEFAULT_AGE, colorIndex = kids.view().children.size % CATEGORY_COLORS.size)
                } else {
                    it.copy(
                        loaded = true, name = c.name, birthYear = c.birthYear, colorIndex = CATEGORY_COLORS.indexOf(c.color).coerceAtLeast(0),
                        weekly = AmountInput.of(c.weekly), jar = c.jarLabel != null || c.jarTarget != null, jarLabel = c.jarLabel.orEmpty(),
                        jarTarget = c.jarTarget?.let { v -> AmountInput.of(v) } ?: AmountInput(), match = c.matchPer != null,
                        matchPer = c.matchPer?.let { v -> AmountInput.of(v) } ?: AmountInput(), matchAdd = c.matchAdd?.let { v -> AmountInput.of(v) } ?: AmountInput(),
                    )
                }
            }
        }
    }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun setBirthYear(year: Int) = _form.update { it.copy(birthYear = year.coerceIn(thisYear - 25, thisYear)) }
    fun setColor(index: Int) = _form.update { it.copy(colorIndex = index) }
    fun setJar(on: Boolean) = _form.update { it.copy(jar = on, match = it.match && on) }
    fun setJarLabel(text: String) = _form.update { it.copy(jarLabel = text.take(LABEL_MAX)) }
    fun setMatch(on: Boolean) = _form.update { it.copy(match = on) }

    private fun change(field: ChildAmount, edit: (AmountInput) -> AmountInput) = _form.update {
        when (field) {
            ChildAmount.WEEKLY -> it.copy(weekly = edit(it.weekly))
            ChildAmount.TARGET -> it.copy(jarTarget = edit(it.jarTarget))
            ChildAmount.PER -> it.copy(matchPer = edit(it.matchPer))
            ChildAmount.ADD -> it.copy(matchAdd = edit(it.matchAdd))
        }
    }

    fun digit(field: ChildAmount, d: Int) = change(field) { it.digit(d) }
    fun point(field: ChildAmount) = change(field) { it.point() }
    fun backspace(field: ChildAmount) = change(field) { it.backspace() }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val label = if (f.jar) f.jarLabel.trim().ifEmpty { null } else null
                val target = if (f.jar) f.jarTarget.toPiastres().takeIf { it > 0 } else null
                val per = if (f.jar && f.match) f.matchPer.toPiastres() else null
                val add = if (f.jar && f.match) f.matchAdd.toPiastres() else null
                val color = CATEGORY_COLORS[f.colorIndex]
                val old = original
                if (old == null) {
                    kids.addChild(f.name, f.birthYear, color, f.weekly.toPiastres(), label, target, per, add)
                } else {
                    kids.updateChild(old.copy(name = f.name, birthYear = f.birthYear, color = color, weekly = f.weekly.toPiastres(), jarLabel = label, jarTarget = target, matchPer = per, matchAdd = add))
                }
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
        viewModelScope.launch {
            try {
                kids.deleteChild(id)
                _gone.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "delete failed", e)
            }
        }
    }
}
