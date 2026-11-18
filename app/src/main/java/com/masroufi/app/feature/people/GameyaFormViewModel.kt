package com.masroufi.app.feature.people

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.repo.FREE_GAMEYAS
import com.masroufi.app.data.repo.GameyaRepository
import com.masroufi.app.data.repo.NewGameyaMember
import com.masroufi.app.data.repo.PlusRequired
import com.masroufi.app.data.repo.PocketRepository
import com.masroufi.app.entitlements.Entitlements
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
import java.time.YearMonth
import javax.inject.Inject

data class GameyaFormState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val name: String = "",
    val amount: AmountInput = AmountInput(),
    /** The others, in turn order; the user is not in the list. */
    val members: List<String> = emptyList(),
    val newMember: String = "",
    val start: YearMonth = YearMonth.of(1970, 1),
    val myTurn: Int = 1,
    val pocketId: Long? = null,
    val pockets: List<PocketEntity> = emptyList(),
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: Boolean = false,
    /** Free already has its one gameya: the screen shows the Plus card instead of the form. */
    val blocked: Boolean = false,
    val askArchive: Boolean = false,
) {
    val participants: Int get() = members.size + 1

    /** The turn number of the member at [index]: the user's seat is skipped. */
    fun turnOf(index: Int): Int = if (index + 1 >= myTurn) index + 2 else index + 1

    val canSave: Boolean
        get() = loaded && !saving && !blocked && name.isNotBlank() && amount.toPiastres() > 0 &&
            (editing || (members.isNotEmpty() && myTurn in 1..participants && pocketId != null))
}

private const val TAG = "GameyaForm"
private const val NAME_MAX = 30
private const val MEMBERS_MAX = 60

/** New gameya (no "id", or 0) and edit gameya (name and monthly amount only; turns move through the swap button). */
@HiltViewModel
class GameyaFormViewModel @Inject constructor(
    private val gameyas: GameyaRepository,
    pocketRepo: PocketRepository,
    private val entitlements: Entitlements,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    private val id: Long = savedState.get<Long>("id") ?: 0L
    val isEdit: Boolean get() = id != 0L

    private val _form = MutableStateFlow(GameyaFormState(editing = id != 0L))
    val form: StateFlow<GameyaFormState> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val pockets = pocketRepo.observePockets().first().map { it.pocket }.filterNot { it.archived }
            val stored = if (id != 0L) gameyas.get(id) else null
            val blocked = id == 0L && !entitlements.isPlus.value && gameyas.observe().first().size >= FREE_GAMEYAS
            _form.update {
                it.copy(
                    loaded = true, pockets = pockets, pocketId = pockets.firstOrNull()?.id, blocked = blocked,
                    start = YearMonth.from(LocalDate.now(clock)),
                    name = stored?.name ?: "", amount = stored?.let { s -> AmountInput.of(s.monthly) } ?: AmountInput(),
                )
            }
        }
    }

    fun setName(text: String) = _form.update { it.copy(name = text.take(NAME_MAX), failed = false) }
    fun digit(d: Int) = _form.update { it.copy(amount = it.amount.digit(d)) }
    fun point() = _form.update { it.copy(amount = it.amount.point()) }
    fun backspace() = _form.update { it.copy(amount = it.amount.backspace()) }
    fun setPocket(pocketId: Long) = _form.update { it.copy(pocketId = pocketId) }
    fun setNewMember(text: String) = _form.update { it.copy(newMember = text.take(NAME_MAX)) }

    /** Adds the typed name at the end of the turn order. */
    fun addMember() = _form.update {
        val name = it.newMember.trim()
        if (name.isEmpty() || it.members.size >= MEMBERS_MAX) it else it.copy(members = it.members + name, newMember = "")
    }

    /** Removes the other member sitting at [turn]; the user's own seat stays. */
    fun removeSeat(turn: Int) = _form.update {
        val index = if (turn < it.myTurn) turn - 1 else turn - 2
        if (turn == it.myTurn || index !in it.members.indices) it
        else it.members.filterIndexed { i, _ -> i != index }.let { rest -> it.copy(members = rest, myTurn = it.myTurn.coerceAtMost(rest.size + 1)) }
    }

    /** Moves the seat with turn number [turn] by [delta] places (-1 earlier, +1 later); the user's seat moves like any other. */
    fun moveSeat(turn: Int, delta: Int) = _form.update {
        val to = turn + delta
        fun index(t: Int) = if (t < it.myTurn) t - 1 else t - 2 // the member list skips the user's seat
        when {
            turn !in 1..it.participants || to !in 1..it.participants -> it
            turn == it.myTurn -> it.copy(myTurn = to)
            to == it.myTurn -> it.copy(myTurn = turn)
            else -> it.copy(members = it.members.toMutableList().also { l -> l.add(index(to), l.removeAt(index(turn))) })
        }
    }

    fun shiftStart(months: Int) = _form.update { it.copy(start = it.start.plusMonths(months.toLong())) }
    fun setMyTurn(turn: Int) = _form.update { it.copy(myTurn = turn.coerceIn(1, it.participants)) }

    fun save() {
        val f = _form.value
        if (!f.canSave) return
        _form.update { it.copy(saving = true, failed = false) }
        viewModelScope.launch {
            try {
                if (f.editing) gameyas.update(id, f.name, f.amount.toPiastres())
                else gameyas.create(f.name, f.amount.toPiastres(), f.start, f.myTurn, f.members.map { m -> NewGameyaMember(m) }, checkNotNull(f.pocketId))
                _form.update { it.copy(saving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PlusRequired) {
                _form.update { it.copy(saving = false, blocked = true) }
            } catch (e: Exception) {
                Log.e(TAG, "save failed", e)
                _form.update { it.copy(saving = false, failed = true) }
            }
        }
    }

    fun askArchive() = _form.update { it.copy(askArchive = true) }
    fun dismissArchive() = _form.update { it.copy(askArchive = false) }

    /** After the question: the gameya leaves the list and so does its installment. */
    fun archive() {
        if (!isEdit) return
        viewModelScope.launch {
            try {
                gameyas.archive(id)
                _form.update { it.copy(askArchive = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "archive failed", e)
                _form.update { it.copy(askArchive = false, failed = true) }
            }
        }
    }
}
