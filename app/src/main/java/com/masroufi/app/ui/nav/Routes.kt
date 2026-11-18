package com.masroufi.app.ui.nav

import kotlinx.serialization.Serializable

@Serializable data object Onboarding
@Serializable data object Home
@Serializable data object RollTab
@Serializable data object PlanTab
@Serializable data object PeopleTab
@Serializable data class CloseDay(val date: Long)
@Serializable data class ProverbReward(val date: Long)
@Serializable data object Gallery
@Serializable data object Pockets
@Serializable data class PocketDetail(val id: Long)
@Serializable data object NewPocket
@Serializable data class EnvelopeDetail(val id: Long)
@Serializable data object NewEnvelope
@Serializable data class EditEnvelope(val id: Long)
@Serializable data object NewGoal
@Serializable data object FixedCosts
/** [id] 0 = a new fixed cost. */
@Serializable data class FixedCostEdit(val id: Long)
@Serializable data object Subscriptions
/** [id] 0 = a new subscription; [name] pre-fills it (a one-tap chip). */
@Serializable data class SubscriptionEdit(val id: Long, val name: String = "")
/** [id] 0 = a new personal occasion. */
@Serializable data class SeasonEdit(val id: Long)
@Serializable data object Zakat
/** يوم القبض: the ritual for the income [entryId]. */
@Serializable data class PaydayRitual(val entryId: Long)
/** A person's loans, tab and history. */
@Serializable data class PersonDetail(val id: Long)
/** The duty ledger (Task 38). */
@Serializable data object DutyLedger
/** The gameya (Task 39). */
@Serializable data object GameyaScreen
/** [id] 0 = a new gameya. */
@Serializable data class GameyaForm(val id: Long)
/** ألبومك (Task 42). */
@Serializable data object Album
/** ماتش الأسبوع (Task 42). */
@Serializable data object Match
/** قبل ما تشتري and the wishlist (Task 43). */
@Serializable data object BeforeYouBuy
/** سلّتك (Task 44). */
@Serializable data object Basket
/** ملفك (Task 44). */
@Serializable data object Profile
/** وضع آخر الشهر (Task 46). */
@Serializable data object EndOfMonth
/** الإعدادات (Task 54). */
@Serializable data object AppSettings
/** الخصوصية والشروط (Task 54): one screen, two tabs. */
@Serializable data object Legal
/** تراخيص الخطوط (Task 54). */
@Serializable data object Licences
/** Stubs the Phase 11 tasks fill: the lock (Task 55), the backup (Task 56), the notification switches (Task 57). */
@Serializable data object LockSettings
@Serializable data object BackupSettings
@Serializable data object NotificationSettings
/** أقساطي (Task 48). */
@Serializable data object Installments
/** [id] 0 = a new installment plan. */
@Serializable data class InstallmentEdit(val id: Long)
/** مرتب لنفسك (Task 48). */
@Serializable data object Freelance
/** حكاية الشهر (Task 50): the saved story [id]. */
@Serializable data class Story(val id: Long)
/** الحكايات: the saved months (Task 50). */
@Serializable data object StoryList
/** مصروف العيال (Task 51). */
@Serializable data object Kids
/** [id] 0 = a new child. */
@Serializable data class ChildEdit(val id: Long)
/** مصروفي بلس (Task 53): [feature] is the PlusFeature name it was opened from, empty = from nowhere in particular. */
@Serializable data class Paywall(val feature: String = "")
