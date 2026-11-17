package com.masroufi.app.feature.pockets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.repo.CATEGORY_COLORS
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The kinds a new pocket can be, in chip order. */
val NEW_POCKET_KINDS = listOf(PocketKind.CASH, PocketKind.MOBILE_WALLET, PocketKind.BANK, PocketKind.UNDER_TILE)

data class NewPocketForm(
    val name: String = "",
    val kind: PocketKind = PocketKind.MOBILE_WALLET,
    val opening: AmountInput = AmountInput(),
    val countsInTicket: Boolean = true,
    val saving: Boolean = false,
    val failed: Boolean = false,
    /** The pocket was added: the screen goes back. */
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && !saving
}

private const val TAG = "NewPocket"

@HiltViewModel
class NewPocketViewModel @Inject constructor(private val repo: PocketRepository) : ViewModel() {
    private val _form = MutableStateFlow(NewPocketForm())
    val form: StateFlow<NewPocketForm> = _form.asStateFlow()

    /** Once the user flips the switch themselves, picking another kind no longer resets it. */
    private var countsTouched = false

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }

    /** Under-the-tile starts with "counts in ticket" off (the money is set aside); every other kind starts on. */
    fun setKind(kind: PocketKind) = _form.update {
        it.copy(kind = kind, countsInTicket = if (countsTouched) it.countsInTicket else kind != PocketKind.UNDER_TILE)
    }

    fun setCountsInTicket(on: Boolean) {
        countsTouched = true
        _form.update { it.copy(countsInTicket = on) }
    }

    fun digit(d: Int) = _form.update { it.copy(opening = it.opening.digit(d)) }
    fun point() = _form.update { it.copy(opening = it.opening.point()) }
    fun backspace() = _form.update { it.copy(opening = it.opening.backspace()) }

    /** Adds the pocket in the next data colour (the five cycle by how many pockets there are). */
    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                val color = CATEGORY_COLORS[repo.observePockets().first().size % CATEGORY_COLORS.size]
                repo.addPocket(f.name, f.kind, f.opening.toPiastres(), f.countsInTicket, color)
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "addPocket failed", e)
                _form.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    private companion object {
        const val NAME_MAX = 30
    }
}
