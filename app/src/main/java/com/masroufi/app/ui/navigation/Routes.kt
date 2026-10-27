package com.masroufi.app.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TransactionsRoute
@Serializable data object BudgetsRoute
@Serializable data object GoalsRoute
@Serializable data object MoreRoute

@Serializable data class TransactionEditorRoute(val id: String? = null, val type: String = "EXPENSE", val walletId: String? = null)
@Serializable data class TransactionDetailRoute(val id: String)
@Serializable data object WalletsRoute
@Serializable data class WalletDetailRoute(val id: String)
@Serializable data class WalletEditorRoute(val id: String? = null)
@Serializable data class BudgetEditorRoute(val id: String? = null)
@Serializable data class GoalDetailRoute(val id: String)
@Serializable data class GoalEditorRoute(val id: String? = null)
@Serializable data object RecurringRoute
@Serializable data class RecurringEditorRoute(val id: String? = null)
@Serializable data object DebtsRoute
@Serializable data class DebtDetailRoute(val id: String)
@Serializable data class DebtEditorRoute(val id: String? = null)
@Serializable data object GameyaListRoute
@Serializable data class GameyaDetailRoute(val id: String)
@Serializable data class GameyaEditorRoute(val id: String? = null)
@Serializable data object SplitRulesRoute
@Serializable data class SplitEditorRoute(val id: String? = null)
@Serializable data object AnalyticsRoute
@Serializable data object SettingsRoute
@Serializable data object SecurityRoute
@Serializable data object BackupRoute
@Serializable data object PremiumRoute
@Serializable data object CategoriesRoute
