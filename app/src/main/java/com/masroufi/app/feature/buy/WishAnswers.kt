package com.masroufi.app.feature.buy

import android.util.Log
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.repo.WishRepository
import com.masroufi.app.feature.add.AddPrefill
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The jar question of "صرفت نظر": [offer] is what would move (the price, or less when that is all that is spendable). */
data class WishAsk(val wishId: Long, val name: String, val price: Long, val offer: Long)

/**
 * The two answers to a wish that is due, shared by the before-you-buy screen and Home's card.
 * "صرفت نظر" asks about the jar first (no question when nothing is spendable); closing the question answers nothing.
 */
class WishAnswers(private val repo: WishRepository, private val scope: CoroutineScope) {
    private val _ask = MutableStateFlow<WishAsk?>(null)
    val ask: StateFlow<WishAsk?> = _ask.asStateFlow()

    fun changedMind(wish: WishEntity) {
        scope.launch {
            guarded("changedMind") {
                val offer = repo.jarOffer(wish.id)
                if (offer > 0) _ask.value = WishAsk(wish.id, wish.name, wish.price, offer) else repo.changedMind(wish.id, toJar = false)
            }
        }
    }

    fun confirm(toJar: Boolean) {
        val a = _ask.value ?: return
        _ask.value = null
        scope.launch { guarded("confirm") { repo.changedMind(a.wishId, toJar) } }
    }

    fun dismiss() {
        _ask.value = null
    }

    /** The add sheet's prefill for "اشتريتها"; the wish is marked only when that entry is saved. */
    suspend fun bought(id: Long): AddPrefill? = try {
        repo.bought(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "bought failed", e)
        null
    }

    private suspend fun guarded(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "$what failed", e)
        }
    }

    private companion object {
        const val TAG = "WishAnswers"
    }
}
