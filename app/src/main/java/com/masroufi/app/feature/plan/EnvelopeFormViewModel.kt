package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.CategoryDao
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EnvelopeEntity
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.RecurringRow
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.data.repo.EnvelopeRepository
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.data.repo.RecurringRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The kinds the form offers, in chip order; an emergency envelope keeps its kind unless the user picks another. */
val ENVELOPE_KINDS = listOf(EnvelopeKind.SPENDING, EnvelopeKind.FIXED)

data class EnvelopeForm(
    val loaded: Boolean = false,
    val name: String = "",
    val colorIndex: Int = 0,
    val monthly: AmountInput = AmountInput(),
    val kind: EnvelopeKind = EnvelopeKind.SPENDING,
    val categoryIds: Set<Long> = emptySet(),
    /** FIXED only: the fixed cost this envelope holds. */
    val recurringId: Long? = null,
    val categories: List<CategoryEntity> = emptyList(),
    val fixedCosts: List<RecurringRow> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    /** A 5th envelope on Free: the screen shows the Plus card. */
    val plus: Boolean = false,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = loaded && name.isNotBlank() && !saving
}

private const val TAG = "EnvelopeForm"
private const val NAME_MAX = 30

/** New envelope (no "id" argument, or 0) and edit envelope (the id). */
@HiltViewModel
class EnvelopeFormViewModel @Inject constructor(
    private val envelopes: EnvelopeRepository,
    private val recurring: RecurringRepository,
    private val categoryDao: CategoryDao,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    private var stored: EnvelopeEntity? = null

    private val _form = MutableStateFlow(EnvelopeForm())
    val form: StateFlow<EnvelopeForm> = _form.asStateFlow()

    val isEdit: Boolean get() = id != 0L

    init {
        viewModelScope.launch {
            val categories = categoryDao.all().first().sortedBy { it.id }
            val fixed = fixedCosts().first()
            val saved = if (id != 0L) envelopes.saved(id) else null
            stored = saved?.first
            _form.update {
                if (saved == null) {
                    // New: the next data colour (the five cycle by how many envelopes there are).
                    it.copy(loaded = true, categories = categories, fixedCosts = fixed, colorIndex = envelopes.observe().first().size % CATEGORY_COLORS.size)
                } else {
                    val (env, cats) = saved
                    it.copy(
                        loaded = true, categories = categories, fixedCosts = fixed, name = env.name,
                        colorIndex = CATEGORY_COLORS.indexOf(env.color).coerceAtLeast(0), monthly = AmountInput.of(env.monthly),
                        kind = env.kind, categoryIds = cats, recurringId = fixed.firstOrNull { r -> r.envelopeId == id }?.id,
                    )
                }
            }
            // The fixed-cost list stays current while the user adds one from this screen.
            fixedCosts().collect { rows -> _form.update { it.copy(fixedCosts = rows) } }
        }
    }

    /** The fixed costs an envelope can hold: the kids' weekly item belongs to the Kids screen, not here. */
    private fun fixedCosts() = recurring.observe().map { rows -> rows.filter { it.kind != RecurringKind.KIDS } }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun setColor(index: Int) = _form.update { it.copy(colorIndex = index) }
    fun setKind(kind: EnvelopeKind) = _form.update { it.copy(kind = kind, recurringId = if (kind == EnvelopeKind.FIXED) it.recurringId else null) }
    fun toggleCategory(id: Long) = _form.update { it.copy(categoryIds = if (id in it.categoryIds) it.categoryIds - id else it.categoryIds + id) }
    fun pickFixed(id: Long) = _form.update { it.copy(recurringId = if (it.recurringId == id) null else id) }
    fun dismissPlus() = _form.update { it.copy(plus = false) }

    fun digit(d: Int) = _form.update { it.copy(monthly = it.monthly.digit(d)) }
    fun point() = _form.update { it.copy(monthly = it.monthly.point()) }
    fun backspace() = _form.update { it.copy(monthly = it.monthly.backspace()) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val color = CATEGORY_COLORS[f.colorIndex]
                val envelopeId = stored?.let {
                    envelopes.update(it.copy(name = f.name.trim(), color = color, monthly = f.monthly.toPiastres(), kind = f.kind), f.categoryIds)
                    it.id
                } ?: envelopes.create(f.name.trim(), color, f.monthly.toPiastres(), f.kind, f.categoryIds)
                // The fixed cost the envelope holds: the picked one points here, the others do not.
                fixedCosts().first().forEach { r ->
                    val want = f.kind == EnvelopeKind.FIXED && r.id == f.recurringId
                    val item = if (want && r.envelopeId != envelopeId) recurring.get(r.id) else if (!want && r.envelopeId == envelopeId) recurring.get(r.id) else null
                    item?.let { recurring.update(it.copy(envelopeId = if (want) envelopeId else null)) }
                }
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlusRequired) {
                _form.update { it.copy(saving = false, plus = true) }
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _form.update { it.copy(saving = false, failed = true) }
            }
        }
    }
}
