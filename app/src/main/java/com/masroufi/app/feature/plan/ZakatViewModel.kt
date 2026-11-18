package com.masroufi.app.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.core.PRIMARY_CURRENCY
import com.masroufi.app.data.prefs.SettingsStore
import com.masroufi.app.data.repo.JarRepository
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.domain.nisabFromGold
import com.masroufi.app.domain.zakatFitr
import com.masroufi.app.domain.zakatMal
import com.masroufi.app.entitlements.Entitlements
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class ZakatSheet { Money, Nisab, PerPerson }

/** Typed values override the defaults for this visit only; nothing is stored. */
private data class ZakatEdits(
    val zakatable: Long? = null,
    val nisab: Long? = null,
    val people: Int = 1,
    val perPerson: Long = 0,
    val sheet: ZakatSheet? = null,
)

data class ZakatUi(
    val loading: Boolean = true,
    val isPlus: Boolean = false,
    val zakatable: Long = 0,
    /** Null until the user types it (or a gold price exists). */
    val nisab: Long? = null,
    val people: Int = 1,
    val perPerson: Long = 0,
    val sheet: ZakatSheet? = null,
) {
    /** Null while there is no nisab to compare with; 0 below it. */
    val zakat: Long? get() = nisab?.let { zakatMal(zakatable, it) }
    val fitr: Long get() = zakatFitr(people, perPerson)
}

@HiltViewModel
class ZakatViewModel @Inject constructor(
    pockets: PocketRepository,
    jar: JarRepository,
    settings: SettingsStore,
    entitlements: Entitlements,
) : ViewModel() {
    private val edits = MutableStateFlow(ZakatEdits())

    val state: StateFlow<ZakatUi> = combine(
        pockets.observePockets(), jar.observe(), settings.settings, entitlements.isPlus, edits,
    ) { ps, j, s, plus, e ->
        val held = ps.filter { it.pocket.currency == PRIMARY_CURRENCY }.sumOf { it.balance.coerceAtLeast(0) } + j.total.coerceAtLeast(0)
        ZakatUi(
            loading = false, isPlus = plus, zakatable = e.zakatable ?: held,
            nisab = e.nisab ?: s.goldPricePerGram?.let(::nisabFromGold),
            people = e.people, perPerson = e.perPerson, sheet = e.sheet,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ZakatUi())

    fun open(s: ZakatSheet) = edits.update { it.copy(sheet = s) }
    fun dismiss() = edits.update { it.copy(sheet = null) }
    fun setZakatable(v: Long) = edits.update { it.copy(zakatable = v, sheet = null) }
    fun setNisab(v: Long) = edits.update { it.copy(nisab = v, sheet = null) }
    fun setPerPerson(v: Long) = edits.update { it.copy(perPerson = v, sheet = null) }
    fun stepPeople(delta: Int) = edits.update { it.copy(people = (it.people + delta).coerceIn(0, 99)) }
}
