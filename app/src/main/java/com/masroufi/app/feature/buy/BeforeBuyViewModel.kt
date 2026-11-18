package com.masroufi.app.feature.buy

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.repo.NOTE_MAX_CHARS
import com.masroufi.app.data.repo.WishRepository
import com.masroufi.app.data.repo.WishView
import com.masroufi.app.domain.BeforeBuy
import com.masroufi.app.domain.BuyContext
import com.masroufi.app.domain.Installment
import com.masroufi.app.domain.beforeBuy
import com.masroufi.app.domain.installment
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.feature.add.AddPrefill
import com.masroufi.app.feature.add.AmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the keypad types into. */
enum class BuyField { PRICE, MONTHLY, MONTHS }

/** The "دي تساوي" tiles, in the order the mock draws them. */
enum class BuyTile { TICKETS, WORK_DAYS, GOLD, SEASON }

/** An installment the shop quoted is never longer than this many months; longer reads as a typo and shows nothing. */
const val MAX_INSTALLMENT_MONTHS = 120

private data class BuyForm(
    val name: String = "",
    val price: AmountInput = AmountInput(),
    val monthly: AmountInput = AmountInput(),
    val months: AmountInput = AmountInput(),
    /** The keypad's target; null while the name field has the system keyboard. */
    val field: BuyField? = BuyField.PRICE,
)

data class BeforeBuyUi(
    val loading: Boolean = true,
    val name: String = "",
    val price: AmountInput = AmountInput(),
    val monthly: AmountInput = AmountInput(),
    val months: AmountInput = AmountInput(),
    val field: BuyField? = BuyField.PRICE,
    val plus: Boolean = false,
    /** Null until a price is typed. */
    val result: BeforeBuy? = null,
    /** Only the tiles that have data. */
    val tiles: List<BuyTile> = emptyList(),
    /** Plus only, and only once the payment and the months are typed. */
    val installment: Installment? = null,
    val wishes: List<WishView> = emptyList(),
    val changedMindTotal: Long = 0,
    /** Counts successful parks; the screen plays the card-into-list motion for each. */
    val parked: Int = 0,
) {
    val priceValue: Long get() = price.toPiastres()
    val canPark: Boolean get() = name.isNotBlank() && priceValue > 0
}

@HiltViewModel
class BeforeBuyViewModel @Inject constructor(
    private val repo: WishRepository,
    entitlements: Entitlements,
) : ViewModel() {
    private val form = MutableStateFlow(BuyForm())
    private val context = MutableStateFlow<BuyContext?>(null)
    private val parked = MutableStateFlow(0)
    private val answers = WishAnswers(repo, viewModelScope)
    private val prefills = Channel<AddPrefill>(Channel.BUFFERED)

    /** "اشتريتها": the add sheet's prefill; the wish is marked bought when that entry is saved, not before. */
    val prefill: Flow<AddPrefill> = prefills.receiveAsFlow()

    /** The jar question of "صرفت نظر" (the sheet shows while this is set). */
    val ask: StateFlow<WishAsk?> = answers.ask

    private val list = combine(repo.observe(), repo.totalChangedMind(), ::Pair)

    val state: StateFlow<BeforeBuyUi> = combine(form, context, list, entitlements.isPlus, parked) { f, ctx, (wishes, total), plus, parkedCount ->
        val price = f.price.toPiastres()
        val result = if (price > 0 && ctx != null) beforeBuy(price, ctx) else null
        BeforeBuyUi(
            loading = ctx == null,
            name = f.name, price = f.price, monthly = f.monthly, months = f.months, field = f.field,
            plus = plus,
            result = result,
            tiles = result?.tiles().orEmpty(),
            installment = if (plus) installment(price, f.monthly.toPiastres(), f.months.monthsOrZero()) else null,
            wishes = wishes,
            changedMindTotal = total,
            parked = parkedCount,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BeforeBuyUi())

    init {
        viewModelScope.launch {
            try {
                context.value = repo.context()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "context failed", e)
                context.value = BuyContext(null, null, null, null, null) // nothing known: only the price and the list work
            }
        }
    }

    fun setName(text: String) = form.update { it.copy(name = text.take(NOTE_MAX_CHARS)) }

    /** The keypad's target; null hands the keyboard to the name field. */
    fun focus(field: BuyField?) = form.update { it.copy(field = field) }

    fun digit(d: Int) = type { it.digit(d) }
    fun point() = type { it.point() }
    fun backspace() = type { it.backspace() }

    private fun type(op: (AmountInput) -> AmountInput) = form.update { f ->
        when (f.field) {
            BuyField.PRICE -> f.copy(price = op(f.price))
            BuyField.MONTHLY -> f.copy(monthly = op(f.monthly))
            // Whole months only: a point does nothing here.
            BuyField.MONTHS -> f.copy(months = op(f.months).let { if (it.hasPoint) f.months else it })
            null -> f
        }
    }

    /**
     * Parks the typed item for 48 hours and clears the form at once (so a second tap parks nothing); the installment is
     * stored only for Plus and only when both numbers were typed. False when there is no name or no price.
     */
    fun park(): Boolean {
        val f = form.value
        val price = f.price.toPiastres()
        if (f.name.isBlank() || price <= 0) return false
        val plus = state.value.plus
        val monthly = f.monthly.toPiastres().takeIf { plus && it > 0 }
        val months = f.months.monthsOrZero().takeIf { monthly != null && it > 0 }
        form.value = BuyForm(field = f.field)
        parked.update { it + 1 }
        viewModelScope.launch {
            try {
                repo.park(f.name, price, monthly.takeIf { months != null }, months)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "park failed", e)
                form.value = f // nothing is lost: what was typed comes back
            }
        }
        return true
    }

    fun changedMind(wish: WishEntity) = answers.changedMind(wish)
    fun jarAnswer(toJar: Boolean) = answers.confirm(toJar)
    fun askDismissed() = answers.dismiss()

    fun bought(id: Long) {
        viewModelScope.launch { answers.bought(id)?.let { prefills.send(it) } }
    }

    private companion object {
        const val TAG = "BeforeBuy"
    }
}

/** The tiles with something to say: each needs its number and the number must not round to nothing. */
internal fun BeforeBuy.tiles(): List<BuyTile> = buildList {
    if ((tickets?.value ?: 0) > 0) add(BuyTile.TICKETS)
    if ((workDays?.value ?: 0) > 0) add(BuyTile.WORK_DAYS)
    if ((goldGramsHundredths ?: 0) > 0) add(BuyTile.GOLD)
    if ((seasonSharePercent ?: 0) > 0) add(BuyTile.SEASON)
}

/** The typed months as a whole number; 0 when empty or past [MAX_INSTALLMENT_MONTHS]. */
internal fun AmountInput.monthsOrZero(): Int = text.toIntOrNull()?.takeIf { it in 1..MAX_INSTALLMENT_MONTHS } ?: 0
