package com.masroufi.app.ui.components

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.annotation.StringRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.masroufi.app.R
import com.masroufi.app.data.repo.FinanceError
import com.masroufi.app.data.repo.FinanceException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

sealed interface EditorEffect {
    data object Done : EditorEffect
    data class Failed(val error: FinanceError) : EditorEffect
    data object UnexpectedError : EditorEffect
}

/** Fire-once effects channel; buffers while the screen is in the background instead of dropping. */
class EffectChannel<T> {
    private val channel = Channel<T>(Channel.BUFFERED)
    val flow: Flow<T> = channel.receiveAsFlow()
    fun send(effect: T) {
        channel.trySend(effect)
    }
}

/** Runs [block], mapping validation failures and unexpected errors to an [EditorEffect]. */
suspend fun EffectChannel<EditorEffect>.attempt(block: suspend () -> Unit) {
    try {
        block()
        send(EditorEffect.Done)
    } catch (e: FinanceException) {
        send(EditorEffect.Failed(e.error))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        send(EditorEffect.UnexpectedError)
    }
}

@Composable
fun <T> CollectEffects(flow: Flow<T>, onEffect: suspend (T) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler = rememberUpdatedState(onEffect)
    LaunchedEffect(flow, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { handler.value(it) }
        }
    }
}

@StringRes
fun FinanceError.messageRes(): Int = when (this) {
    FinanceError.AMOUNT_REQUIRED -> R.string.error_amount_required
    FinanceError.NAME_REQUIRED -> R.string.error_name_required
    FinanceError.WALLET_REQUIRED -> R.string.error_wallet_required
    FinanceError.SAME_WALLET_TRANSFER -> R.string.error_same_wallet
    FinanceError.TARGET_AMOUNT_REQUIRED -> R.string.error_target_amount
    FinanceError.WITHDRAW_EXCEEDS_SAVED -> R.string.error_withdraw_exceeds
    FinanceError.PAYMENT_EXCEEDS_REMAINING -> R.string.error_payment_exceeds
    FinanceError.WALLET_HAS_TRANSACTIONS -> R.string.error_wallet_has_transactions
    FinanceError.MEMBERS_REQUIRED -> R.string.error_members_required
    FinanceError.NOT_FOUND -> R.string.error_not_found
}

@StringRes
fun EditorEffect.messageRes(): Int? = when (this) {
    EditorEffect.Done -> null
    is EditorEffect.Failed -> error.messageRes()
    EditorEffect.UnexpectedError -> R.string.error_unexpected
}

/** Standard editor handling: close on success, otherwise explain the problem in a snackbar and stay. */
@Composable
fun HandleEditorEffects(flow: Flow<EditorEffect>, snackbar: SnackbarHostState, onDone: () -> Unit) {
    val context = LocalContext.current
    val haptics = rememberHaptics()
    CollectEffects(flow) { effect ->
        val res = effect.messageRes()
        if (res == null) {
            haptics.confirm()
            onDone()
        } else {
            haptics.reject()
            snackbar.showSnackbar(context.getString(res))
        }
    }
}
