package com.masroufi.app.entitlements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Clock
import java.time.LocalDate

enum class PlusPlan { YEARLY, MONTHLY }

/** A store product: [priceText] is the store's own formatting; [priceMicros]/[currency] let the UI compute savings. */
data class BillingProduct(
    val plan: PlusPlan,
    val priceText: String,
    val priceMicros: Long,
    val currency: String,
    /** True for the stub's mock prices: the UI marks them "(debug)". */
    val debug: Boolean = false,
)

sealed interface PurchaseResult {
    data object Success : PurchaseResult
    data object Cancelled : PurchaseResult
    data object Failed : PurchaseResult
}

data class ActiveSubscription(val plan: PlusPlan, val renewsOn: LocalDate)

/** Billing behind an interface: the Google Play implementation arrives in Phase 13 (no Play Console yet). */
interface Billing {
    /** Empty = prices are not available (the paywall says so and disables its button). */
    val products: StateFlow<List<BillingProduct>>
    val subscription: StateFlow<ActiveSubscription?>
    suspend fun purchase(plan: PlusPlan): PurchaseResult
    /** Re-reads what the store knows; returns the active subscription, if any. */
    suspend fun restore(): ActiveSubscription?
    /** Where "إدارة الاشتراك" goes (Google Play's subscriptions page). */
    fun manageSubscriptionUrl(): String
}

const val MANAGE_SUBSCRIPTION_URL = "https://play.google.com/store/account/subscriptions"

/**
 * Release: no products, never subscribed, purchase always fails (nothing is hard-coded). Debug: the mock's two prices
 * marked debug, and a purchase outcome the gallery sets with [nextOutcome]; a success makes an in-memory subscription.
 */
class StubBilling(private val debug: Boolean, private val clock: Clock = Clock.systemDefaultZone()) : Billing {
    private val productsState = MutableStateFlow(
        if (debug) {
            listOf(
                BillingProduct(PlusPlan.YEARLY, "399 جنيه (debug)", 399_000_000, "EGP", debug = true),
                BillingProduct(PlusPlan.MONTHLY, "49 جنيه (debug)", 49_000_000, "EGP", debug = true),
            )
        } else {
            emptyList()
        },
    )
    private val subscriptionState = MutableStateFlow<ActiveSubscription?>(null)
    override val products: StateFlow<List<BillingProduct>> = productsState.asStateFlow()
    override val subscription: StateFlow<ActiveSubscription?> = subscriptionState.asStateFlow()

    /** Debug only (the gallery): what the next [purchase] returns. */
    @Volatile var nextOutcome: PurchaseResult = PurchaseResult.Success

    override suspend fun purchase(plan: PlusPlan): PurchaseResult {
        if (!debug || productsState.value.none { it.plan == plan }) return PurchaseResult.Failed
        val result = nextOutcome
        if (result == PurchaseResult.Success) {
            val today = LocalDate.now(clock)
            subscriptionState.value = ActiveSubscription(plan, if (plan == PlusPlan.YEARLY) today.plusYears(1) else today.plusMonths(1))
        }
        return result
    }

    override suspend fun restore(): ActiveSubscription? = subscriptionState.value

    override fun manageSubscriptionUrl(): String = MANAGE_SUBSCRIPTION_URL

    /** Debug only (the gallery): ends the mock subscription. */
    fun clearSubscription() { subscriptionState.value = null }
}
