package com.masroufi.app.feature.paywall

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.masroufi.app.data.repo.FeaturePitch
import com.masroufi.app.data.repo.LossRow
import com.masroufi.app.data.repo.LossRows
import com.masroufi.app.data.repo.LossRowsRepository
import com.masroufi.app.data.repo.MAX_LOSS_ROWS
import com.masroufi.app.data.repo.PLAIN_FEATURES
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.entitlements.Billing
import com.masroufi.app.entitlements.BillingProduct
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.PlusRepository
import com.masroufi.app.entitlements.PurchaseResult
import com.masroufi.app.entitlements.TrialInfo
import com.masroufi.app.entitlements.TrialState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** After a purchase the screen closes itself this long after the success (09-PREMIUM "Motion and haptics"). */
const val CLOSE_AFTER_PURCHASE_MS = 900L

enum class PurchaseState { Idle, Buying, Done, Failed }

/** Which headline is true for the user's trial (see [headlineFor]). */
enum class Headline { Tomorrow, Today, Later, Ended, Adds, Plain }

/** One paywall row: a [LossRow] (with the pitch's line when opened from that feature) or a feature with no row of its own. */
sealed interface PaywallRow {
    val feature: PlusFeature

    data class Loss(val row: LossRow, val pitch: FeaturePitch?) : PaywallRow {
        override val feature get() = row.feature
    }

    data class Pitch(val pitch: FeaturePitch) : PaywallRow {
        override val feature get() = pitch.feature
    }
}

/** [savePercent] is on the yearly plan only; [perMonth] whole currency units (yearly only), both from the store's micros. */
data class PlanUi(
    val plan: PlusPlan,
    val priceText: String,
    val currency: String,
    val savePercent: Int? = null,
    val perMonth: Long? = null,
)

data class PaywallUi(
    val loading: Boolean = true,
    val first: PlusFeature? = null,
    val trialState: TrialState = TrialState.NOT_YET,
    val daysLeft: Int = 0,
    val endDay: LocalDate? = null,
    val headline: Headline = Headline.Plain,
    val rows: List<PaywallRow> = emptyList(),
    /** The "tried the free version" page: no row from the user's data. */
    val plain: Boolean = false,
    val plans: List<PlanUi> = emptyList(),
    val selected: PlusPlan = PlusPlan.YEARLY,
    val purchase: PurchaseState = PurchaseState.Idle,
    /** The screen should close itself (900 ms after a successful purchase). */
    val close: Boolean = false,
) {
    /** Release stub: no store prices, so the screen says so and the button stays off. */
    val pricesAvailable: Boolean get() = plans.isNotEmpty()
    val canBuy: Boolean get() = pricesAvailable && (purchase == PurchaseState.Idle || purchase == PurchaseState.Failed)

    /** Something the user has is about to stop or has stopped: only then are the rows "about to be torn off". */
    val stops: Boolean get() = trialState == TrialState.ACTIVE || trialState == TrialState.ENDED
}

internal fun headlineFor(trial: TrialInfo, plain: Boolean): Headline = when {
    plain -> Headline.Plain
    trial.state == TrialState.ACTIVE -> when (trial.daysLeft) {
        1 -> Headline.Tomorrow
        0 -> Headline.Today
        else -> Headline.Later
    }
    trial.state == TrialState.ENDED -> Headline.Ended
    else -> Headline.Adds
}

/**
 * Rows from [lr]: at most five, the feature the paywall was opened from on top (its [LossRow] if it has one, with the
 * pitch's line, else a pitch row of its own). No loss rows = the plain version with three features.
 */
internal fun paywallRows(lr: LossRows): List<PaywallRow> {
    val pitch = lr.first
    val losses = lr.rows.map { PaywallRow.Loss(it, pitch?.takeIf { p -> p.feature == it.feature }) }
    return when {
        losses.isNotEmpty() ->
            (if (pitch != null && losses.none { it.feature == pitch.feature }) listOf(PaywallRow.Pitch(pitch)) + losses else losses)
                .take(MAX_LOSS_ROWS)
        pitch != null -> (listOf(PaywallRow.Pitch(pitch)) + lr.plainFeatures.filter { it != pitch.feature }.map { PaywallRow.Pitch(FeaturePitch(it)) })
            .take(PLAIN_FEATURES.size)
        else -> lr.plainFeatures.map { PaywallRow.Pitch(FeaturePitch(it)) }
    }
}

/**
 * Yearly first, then monthly, as the store returns them. The saving is against twelve monthly payments and the
 * per-month figure is the yearly price over twelve, both from the store's micros (Long math, rounded down); nothing here
 * is a price of its own.
 */
internal fun plansUi(products: List<BillingProduct>): List<PlanUi> {
    val yearly = products.firstOrNull { it.plan == PlusPlan.YEARLY }
    val monthly = products.firstOrNull { it.plan == PlusPlan.MONTHLY }
    val year = yearly?.let { y ->
        val full = monthly?.takeIf { it.priceMicros > 0 }?.let { it.priceMicros * 12 }
        val save = full?.let { ((it - y.priceMicros) * 100 / it).toInt() }?.takeIf { it > 0 }
        PlanUi(PlusPlan.YEARLY, y.priceText, y.currency, save, y.priceMicros / 12 / MICROS)
    }
    val month = monthly?.let { PlanUi(PlusPlan.MONTHLY, it.priceText, it.currency) }
    return listOfNotNull(year, month)
}

private const val MICROS = 1_000_000L
private const val TAG = "Paywall"

private data class Loaded(val trial: TrialInfo, val rows: LossRows)

private data class Local(val pick: PlusPlan = PlusPlan.YEARLY, val purchase: PurchaseState = PurchaseState.Idle, val close: Boolean = false)

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: Billing,
    private val lossRows: LossRowsRepository,
    private val plus: PlusRepository,
    private val clock: Clock,
    savedState: SavedStateHandle,
) : ViewModel() {
    /** The Plus feature the user came from (the route's "feature" argument, empty = none). */
    val first: PlusFeature? = savedState.get<String>("feature")?.let { n -> PlusFeature.values().firstOrNull { it.name == n } }

    private val loaded = MutableStateFlow<Loaded?>(null)
    private val local = MutableStateFlow(Local())

    val state: StateFlow<PaywallUi> = combine(loaded, billing.products, local) { l, products, loc ->
        if (l == null) return@combine PaywallUi(first = first)
        val plans = plansUi(products)
        val plain = l.rows.plain
        PaywallUi(
            loading = false, first = first, trialState = l.trial.state, daysLeft = l.trial.daysLeft, endDay = l.trial.endDay,
            headline = headlineFor(l.trial, plain), rows = paywallRows(l.rows), plain = plain, plans = plans,
            selected = selectedPlan(plans, loc.pick), purchase = loc.purchase, close = loc.close,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PaywallUi(first = first))

    init {
        viewModelScope.launch {
            loaded.value = try {
                Loaded(plus.trialInfoNow(LocalDate.now(clock)), lossRows.build(first))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing invented: without the data the plain version shows.
                Log.e(TAG, "paywall rows failed", e)
                Loaded(TrialInfo(TrialState.NOT_YET, null, null, 0, false), LossRows(emptyList(), first?.let { FeaturePitch(it) }, PLAIN_FEATURES))
            }
        }
    }

    private fun selectedPlan(plans: List<PlanUi>, pick: PlusPlan) = if (plans.any { it.plan == pick }) pick else plans.firstOrNull()?.plan ?: pick

    /** A plan card was tapped (the screen fires Tick). */
    fun select(plan: PlusPlan) {
        if (local.value.purchase == PurchaseState.Buying || local.value.purchase == PurchaseState.Done) return
        local.update { it.copy(pick = plan) }
    }

    /**
     * The button (the screen fires Confirm). Ignored without prices, while a purchase runs and after one succeeded. Success:
     * [PurchaseState.Done], then [CLOSE_AFTER_PURCHASE_MS] later [PaywallUi.close]. Cancel or failure: [PurchaseState.Failed],
     * the screen stays and says nothing was charged.
     */
    fun buy() {
        val loc = local.value
        if (loc.purchase == PurchaseState.Buying || loc.purchase == PurchaseState.Done) return
        val plans = plansUi(billing.products.value)
        if (plans.isEmpty()) return
        val plan = selectedPlan(plans, loc.pick)
        local.update { it.copy(purchase = PurchaseState.Buying) }
        viewModelScope.launch {
            val result = try {
                billing.purchase(plan)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "purchase failed", e)
                PurchaseResult.Failed
            }
            if (result == PurchaseResult.Success) {
                local.update { it.copy(purchase = PurchaseState.Done) }
                delay(CLOSE_AFTER_PURCHASE_MS)
                local.update { it.copy(close = true) }
            } else {
                local.update { it.copy(purchase = PurchaseState.Failed) }
            }
        }
    }
}
