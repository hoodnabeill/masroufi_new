package com.masroufi.app.feature.basket

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.BasketData
import com.masroufi.app.data.repo.BasketItemView
import com.masroufi.app.data.repo.BasketRepository
import com.masroufi.app.data.repo.BasketResult
import com.masroufi.app.data.repo.HabitFacts
import com.masroufi.app.data.repo.SuggestedTicket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The one context line under an item's name: only what the data supports. */
sealed interface ItemLine {
    /** "آخر مرة من [place] · كان [was]": [was] is the price the change badge starts from; null = none or unchanged. */
    data class LastPlace(val place: String, val was: Long?) : ItemLine
    data class Cheapest(val place: String, val price: Long) : ItemLine
    data class Times(val perMonth: Int) : ItemLine
    data object Fell : ItemLine
    data object Flat : ItemLine
}

data class BasketItemUi(val id: Long, val name: String, val lastPrice: Long?, val changePercent: Int?, val line: ItemLine?)

enum class Trend { UP, DOWN, SAME }

/** The change hero: [sinceMonth] 1..12 is the month the "then" total starts from. */
data class HeroUi(val percent: Int, val then: Long, val now: Long, val sinceMonth: Int?, val trend: Trend)

data class BasketUi(
    val loading: Boolean = true,
    val locked: Boolean = false,
    val items: List<BasketItemUi> = emptyList(),
    val hero: HeroUi? = null,
    val suggestion: SuggestedTicket? = null,
    /** The raised ticket just accepted; shown in place of the card until the screen closes. */
    val raisedTo: Long? = null,
    val habits: HabitFacts = HabitFacts(null, null, null),
    val addSheet: Boolean = false,
) {
    val empty: Boolean get() = !loading && !locked && items.isEmpty()
    val hasHabits: Boolean get() = habits.weekday != null || habits.lateNight != null || habits.cashVsWalletPercent != null
}

/** What the basket screen calls on the view model (a stateless screen takes this, so a test can pass a no-op). */
interface BasketActions {
    fun openAdd() {}
    fun dismissAdd() {}
    fun track(name: String) {}
    fun raise() {}
    fun leave() {}
}

private const val TAG = "Basket"

/** "N times a month" is worth saying from about weekly. */
private const val TIMES_MIN = 4

/** "Steady for 3 months" needs the window to really cover them. */
private const val FLAT_DAYS = 80

internal fun itemLine(v: BasketItemView): ItemLine? = when {
    v.lastPlace != null -> ItemLine.LastPlace(v.lastPlace, v.firstPrice?.takeIf { it != v.lastPrice })
    v.cheapest != null && v.lastPrice != null && v.cheapest.price < v.lastPrice -> ItemLine.Cheapest(v.cheapest.place, v.cheapest.price)
    v.timesPerMonth != null && v.timesPerMonth >= TIMES_MIN -> ItemLine.Times(v.timesPerMonth)
    v.changePercent != null && v.changePercent < 0 -> ItemLine.Fell
    v.changePercent == 0 && (v.windowDays ?: 0) >= FLAT_DAYS -> ItemLine.Flat
    else -> null
}

internal fun BasketData.toUi(): Triple<List<BasketItemUi>, HeroUi?, SuggestedTicket?> = Triple(
    items.map { BasketItemUi(it.item.id, it.item.name, it.lastPrice, it.changePercent, itemLine(it)) },
    change?.let { HeroUi(it.percent, it.then, it.now, since?.monthValue, if (it.percent > 0) Trend.UP else if (it.percent < 0) Trend.DOWN else Trend.SAME) },
    suggestion,
)

@HiltViewModel
class BasketViewModel @Inject constructor(private val basket: BasketRepository) : ViewModel(), BasketActions {
    private val addSheet = MutableStateFlow(false)
    private val raisedTo = MutableStateFlow<Long?>(null)

    val state: StateFlow<BasketUi> = combine(basket.observe(), addSheet, raisedTo) { r, add, raised ->
        when (r) {
            BasketResult.Locked -> BasketUi(loading = false, locked = true)
            is BasketResult.Ready -> {
                val (items, hero, suggestion) = r.data.toUi()
                BasketUi(
                    loading = false, items = items, hero = hero, suggestion = suggestion, raisedTo = raised,
                    habits = r.data.habits, addSheet = add,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BasketUi())

    override fun openAdd() { addSheet.value = true }
    override fun dismissAdd() { addSheet.value = false }

    /** "تابع سعر حاجة تانية". */
    override fun track(name: String) {
        addSheet.value = false
        if (name.isNotBlank()) act { basket.track(name) }
    }

    /** "زوّد التذكرة". */
    override fun raise() { act { raisedTo.value = basket.applySuggestedTicket() } }

    /** "سيبها": nothing more this month. */
    override fun leave() { act { basket.dismissSuggestion() } }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "basket action failed", e)
            }
        }
    }
}
