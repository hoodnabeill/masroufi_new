package com.masroufi.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
/** The merged "الخطة" tab; [tab] picks budgets or goals. */
@Serializable data class PlanRoute(val tab: String = "budgets")
@Serializable data object MoreRoute

@Serializable data class TransactionEditorRoute(
    val id: String? = null,
    val type: String = "EXPENSE",
    val walletId: String? = null,
    val categoryId: String? = null,
    /** Prefilled by Home quick-log (typed or spoken) and quick chips. */
    val amount: String? = null,
    val note: String? = null,
)
/**
 * The quick-add bottom sheet for new entries. Its arguments mirror [TransactionEditorRoute] (minus the id) so the
 * same editor view model decodes either route.
 */
@Serializable data class AddSheetRoute(
    val type: String = "EXPENSE",
    val walletId: String? = null,
    val categoryId: String? = null,
    val amount: String? = null,
    val note: String? = null,
)
/** Shown after the sheet saves; [type] is the saved entry's type. */
@Serializable data class RewardRoute(val type: String = "EXPENSE")
@Serializable data class TransactionDetailRoute(val id: String)
@Serializable data object WalletsRoute
@Serializable data class WalletDetailRoute(val id: String)
@Serializable data class WalletEditorRoute(val id: String? = null)
@Serializable data class BudgetEditorRoute(val id: String? = null)
@Serializable data class GoalDetailRoute(val id: String)
@Serializable data class GoalEditorRoute(val id: String? = null)
@Serializable data object RecurringRoute
@Serializable data object SubscriptionsRoute
@Serializable data object AchievementsRoute
@Serializable data class RecurringEditorRoute(val id: String? = null, val serviceKey: String? = null)
@Serializable data object DebtsRoute
@Serializable data class DebtDetailRoute(val id: String)
/** [amount] is prefilled when a سلفة starts from the quick-add sheet. */
@Serializable data class DebtEditorRoute(val id: String? = null, val amount: String? = null)
@Serializable data object GameyaListRoute
@Serializable data class GameyaDetailRoute(val id: String)
@Serializable data class GameyaEditorRoute(val id: String? = null)
@Serializable data object SplitRulesRoute
@Serializable data class SplitEditorRoute(val id: String? = null)
@Serializable data object AnalyticsRoute
/** The monthly story (Phase 6). */
@Serializable data object RecapRoute
/** [doc] is "privacy" or "terms". */
@Serializable data class LegalRoute(val doc: String)
@Serializable data object SettingsRoute
@Serializable data object SecurityRoute
@Serializable data object BackupRoute
@Serializable data object PremiumRoute
@Serializable data object CategoriesRoute

/** One-shot requests from outside the nav graph: onboarding's "I have a backup" opens Backup once the app starts. */
object StartRequest {
    @Volatile var backup: Boolean = false
}
