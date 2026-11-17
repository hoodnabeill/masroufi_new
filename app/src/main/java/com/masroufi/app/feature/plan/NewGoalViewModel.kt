package com.masroufi.app.feature.plan

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.JarRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** [months] = null means no date. */
data class GoalForm(val name: String = "", val target: AmountInput = AmountInput(), val months: Int? = null, val saved: Boolean = false) {
    val canSave: Boolean get() = name.isNotBlank() && target.toPiastres() > 0
}

val GOAL_MONTHS = listOf(3, 6, 12)

@HiltViewModel
class NewGoalViewModel @Inject constructor(private val jar: JarRepository, private val clock: Clock) : ViewModel() {
    private val _form = MutableStateFlow(GoalForm())
    val form: StateFlow<GoalForm> = _form

    fun setName(v: String) = _form.update { it.copy(name = v) }
    fun setMonths(v: Int?) = _form.update { it.copy(months = v) }
    fun digit(d: Int) = _form.update { it.copy(target = it.target.digit(d)) }
    fun point() = _form.update { it.copy(target = it.target.point()) }
    fun backspace() = _form.update { it.copy(target = it.target.backspace()) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        viewModelScope.launch {
            try {
                jar.createGoal(f.name.trim(), f.target.toPiastres(), f.months?.let { LocalDate.now(clock).plusMonths(it.toLong()) })
                _form.update { it.copy(saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("NewGoal", "save failed", e)
            }
        }
    }
}
