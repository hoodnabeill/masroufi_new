package com.masroufi.app.feature.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.EntryRow
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.db.GoalEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.db.RecurringRow
import com.masroufi.app.data.db.SeasonEntity
import com.masroufi.app.data.db.SeasonItemEntity
import com.masroufi.app.data.db.SeasonKind
import com.masroufi.app.data.repo.EnvelopeView
import com.masroufi.app.data.repo.GoalView
import com.masroufi.app.data.repo.GoldView
import com.masroufi.app.data.repo.JarSources
import com.masroufi.app.data.repo.JarView
import com.masroufi.app.data.repo.SeasonView
import com.masroufi.app.data.repo.SubscriptionView
import com.masroufi.app.data.repo.TogetherView
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * The Plan tab and every screen behind it at 200% font, right-to-left, animations off (Task 61): envelopes, the jar,
 * seasons, zakat, subscriptions, fixed costs, their forms and sheets. No `allowOverflow` anywhere.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)
    private val sky = 0xFF8FB4FF.toInt()
    private val turquoise = 0xFF35E0D0.toInt()

    private fun env(id: Long, name: String, left: Long, monthly: Long, kind: EnvelopeKind = EnvelopeKind.SPENDING) = EnvelopeView(
        id = id, name = name, color = if (id % 2 == 0L) sky else turquoise, kind = kind, monthly = monthly, spent = monthly - left, left = left,
        bar = (monthly - left).toFloat() / monthly, sealed = false, paidLabelRes = null, line = null, betterThanLast = false, categoryIds = emptySet(),
    )

    private val cards = listOf(
        EnvelopeCardUi(env(1, "الأكل والشرب", 180_000, 300_000), CardLine.RunsOut(21)),
        EnvelopeCardUi(env(2, "المواصلات", 90_000, 150_000), CardLine.OnPace),
        EnvelopeCardUi(env(3, "الخروجات", 40_000, 100_000), CardLine.BetterThanLast),
        EnvelopeCardUi(env(4, "الفواتير", 0, 120_000, EnvelopeKind.FIXED), CardLine.Sealed(120_000, 5)),
        EnvelopeCardUi(env(5, "حاجات البيت", 70_000, 80_000), null),
    )

    private val envelopes = EnvelopesUi(
        loading = false, cards = cards, split = SplitSummary(1_200_000, 150_000, today),
        tip = MoveTip(1, 2, "الأكل والشرب", "المواصلات", 30_000),
    )

    private fun plan(segment: Int, section: @androidx.compose.runtime.Composable () -> Unit) {
        rule.audit { PlanContent(segment, {}, {}, {}, {}, {}, {}, {}, section) }.assertNone()
    }

    @Test fun envelopesGrid() = plan(0) { EnvelopesSection(envelopes, {}, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun envelopesWithoutTipOrSplit() = plan(0) { EnvelopesSection(envelopes.copy(tip = null, split = SplitSummary(1_200_000, 0, today)), {}, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun envelopesEmpty() = plan(0) { EnvelopesSection(EnvelopesUi(loading = false), {}, {}, {}, { _, _ -> }, {}, {}) }
    @Test fun envelopesLoading() = plan(0) { EnvelopesSection(EnvelopesUi(), {}, {}, {}, { _, _ -> }, {}, {}) }

    @Test fun moveSheet() {
        rule.audit { MoveSheet(EnvelopeSheet.Move(1, 2, 30_000), cards, { _, _, _ -> }, {}) }.assertNone()
    }

    // ---- seasons

    private fun season(id: Long, kind: SeasonKind, name: String, target: Long, saved: Long, days: Long, hijri: Boolean) = SeasonView(
        season = SeasonEntity(id, kind, name, if (hijri) 9 else null, if (hijri) 1 else null, if (hijri) null else today.plusDays(days), target, sky),
        date = today.plusDays(days), approximate = hijri, daysUntil = days, saved = saved, perDayNeeded = if (target > saved) (target - saved) / days else 0,
        items = listOf(SeasonItemEntity(id * 10 + 1, id, "كحك وبسكوت", 150_000), SeasonItemEntity(id * 10 + 2, id, "ياميش", 90_000)),
    )

    private val seasons = listOf(
        season(1, SeasonKind.RAMADAN, "رمضان", 800_000, 300_000, 40, true),
        season(2, SeasonKind.EID_AL_FITR, "عيد الفطر", 500_000, 0, 70, true),
        season(3, SeasonKind.SUMMER, "الصيف", 1_000_000, 250_000, 200, false),
    )

    private val seasonsUi = SeasonsUi(
        loading = false, isPlus = true, seasons = seasons, hero = seasons[0], together = TogetherView(250_000, 200_000, 50_000), togetherCount = 3,
        ledger = mapOf(2L to LedgerNote("خالتي", 60_000)),
    )

    @Test fun seasonsPlus() = plan(1) { SeasonsBody(seasonsUi, {}, {}, {}, SeasonActions.None) }
    @Test fun seasonsReordering() = plan(1) { SeasonsBody(seasonsUi.copy(reordering = true), {}, {}, {}, SeasonActions.None) }
    @Test fun seasonsLocked() = plan(1) { SeasonsBody(seasonsUi.copy(isPlus = false), {}, {}, {}, SeasonActions.None) }
    @Test fun seasonsEmpty() = plan(1) { SeasonsBody(SeasonsUi(loading = false, isPlus = true), {}, {}, {}, SeasonActions.None) }
    @Test fun seasonsLoading() = plan(1) { SeasonsBody(SeasonsUi(), {}, {}, {}, SeasonActions.None) }

    // ---- jar

    private val goals = listOf(
        GoalRowUi(GoalView(GoalEntity(1, "موبايل جديد", 1_500_000), 400_000), today.plusMonths(5)),
        GoalRowUi(GoalView(GoalEntity(2, "سفرية", 3_000_000), 0), null),
    )

    private val jarUi = JarUi(
        loading = false,
        jar = JarView(
            total = 1_250_000, monthGrowth = 80_000, sources = JarSources(6, 700_000, 150_000, 400_000), goals = goals.map { it.view }, free = 850_000,
            gold = GoldView(3_800_000, today, 133, null), firstAt = 1_700_000_000_000,
        ),
        isPlus = true, roundUpStep = 5, season = seasons[0], goals = goals,
    )

    private fun jar(ui: JarUi) = plan(2) { JarContent(ui, {}, {}, {}, JarActions.None) }

    @Test fun jarPlus() = jar(jarUi)
    @Test fun jarWithoutGoldPrice() = jar(jarUi.copy(jar = jarUi.jar!!.copy(gold = null), season = null, goals = emptyList(), roundUpStep = 0))
    @Test fun jarFree() = jar(jarUi.copy(isPlus = false))
    @Test fun jarEmpty() = jar(JarUi(loading = false, jar = JarView(0, 0, JarSources(0, 0, 0, 0), emptyList(), 0, null), isPlus = true))
    @Test fun jarLoading() = jar(JarUi())
    @Test fun jarDepositSheet() = jar(jarUi.copy(sheet = JarSheet.Deposit))
    @Test fun jarWithdrawSheet() = jar(jarUi.copy(sheet = JarSheet.Withdraw))
    @Test fun jarGoldSheet() = jar(jarUi.copy(sheet = JarSheet.Gold))
    @Test fun jarGoalSheet() = jar(jarUi.copy(sheet = JarSheet.Goal(1)))

    @Test fun newGoal() {
        rule.audit { NewGoalContent(GoalForm("موبايل", AmountInput("15000"), 6), {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }
    @Test fun newGoalEmpty() {
        rule.audit { NewGoalContent(GoalForm(), {}, {}, {}, {}, {}, {}, {}) }.assertNone()
    }

    // ---- envelope detail and form

    private fun entry(id: Long) =
        EntryRow(id, EntryKind.SPENT, 3_500, 0, 0, 1_700_000_000_000 + id, null, 1, "أكل", 2, "فودافون كاش", "EGP", true, null, null, null)

    private val detail = EnvelopeDetailUi(
        loading = false, card = cards[0], entries = listOf(entry(1), entry(2)), categories = mapOf(1L to CategoryEntity(1, "أكل", "grid", 0)),
    )

    @Test fun envelopeDetail() {
        rule.audit { EnvelopeDetailContent(detail, {}, {}, {}, {}) }.assertNone()
    }
    @Test fun envelopeDetailSealedAndEmpty() {
        rule.audit { EnvelopeDetailContent(detail.copy(card = cards[3], entries = emptyList()), {}, {}, {}, {}) }.assertNone()
    }
    @Test fun envelopeDetailLoading() {
        rule.audit { EnvelopeDetailContent(EnvelopeDetailUi(), {}, {}, {}, {}) }.assertNone()
    }

    private val categories = listOf("أكل", "مواصلات", "خروجات", "قهوة", "اشتراكات", "ملابس", "هدايا").mapIndexed { i, n -> CategoryEntity(i + 1L, n, "grid", 0) }
    private val bills = listOf(
        RecurringRow(1, "إيجار", 400_000, RecurringKind.BILL, 1, "الكاش", today.plusDays(10), null),
        RecurringRow(2, "كهربا", 60_000, RecurringKind.BILL, 1, "الكاش", today.plusDays(3), null),
    )

    @Test fun envelopeFormSpending() {
        val f = EnvelopeForm(loaded = true, name = "الأكل والشرب", colorIndex = 2, monthly = AmountInput("3000"), categoryIds = setOf(1, 2), categories = categories)
        rule.audit { EnvelopeFormContent(f, false, {}, {}, EnvelopeFormActions.None) }.assertNone()
    }
    @Test fun envelopeFormFixedWithFailureAndPlus() {
        val f = EnvelopeForm(
            loaded = true, name = "الفواتير", kind = EnvelopeKind.FIXED, monthly = AmountInput("1200.5"), recurringId = 1, categories = categories, fixedCosts = bills,
            failed = true, plus = true,
        )
        rule.audit { EnvelopeFormContent(f, true, {}, {}, EnvelopeFormActions.None) }.assertNone()
    }

    // ---- zakat

    private val zakat = ZakatUi(loading = false, isPlus = true, zakatable = 25_000_000, nisab = 20_000_000, people = 4, perPerson = 4_500)

    private fun zakat(ui: ZakatUi) {
        rule.audit { ZakatContent(ui, {}, ZakatActions.None) }.assertNone()
    }

    @Test fun zakatAbove() = zakat(zakat)
    @Test fun zakatBelowAndNoNisab() = zakat(zakat.copy(nisab = null))
    @Test fun zakatLocked() = zakat(zakat.copy(isPlus = false))
    @Test fun zakatLoading() = zakat(ZakatUi())
    @Test fun zakatSheet() = zakat(zakat.copy(sheet = ZakatSheet.Nisab))

    // ---- season detail

    private fun seasonDetail(f: SeasonForm, v: SeasonView?, isNew: Boolean, sheet: SeasonSheet? = null) {
        rule.audit { SeasonDetailContent(f, v, 500_000, sheet, isNew, true, {}, SeasonDetailActions.None) }.assertNone()
    }

    private val seasonForm = SeasonForm(loaded = true, name = "عيد ميلاد ماما", month = 3, day = 14, target = AmountInput("4000"))

    @Test fun seasonHijri() = seasonDetail(seasonForm, seasons[0], false)
    @Test fun seasonPersonal() = seasonDetail(seasonForm, season(9, SeasonKind.PERSONAL, "عيد ميلاد ماما", 400_000, 100_000, 90, false), false)
    @Test fun seasonNew() = seasonDetail(seasonForm.copy(name = ""), null, true)
    @Test fun seasonItemSheet() = seasonDetail(seasonForm, seasons[2], false, SeasonSheet.Item)
    @Test fun seasonMoneySheet() = seasonDetail(seasonForm, seasons[2], false, SeasonSheet.Money)

    // ---- subscriptions

    private val pockets = listOf(
        PocketEntity(1, "الكاش", PocketKind.CASH, color = 0), PocketEntity(2, "فودافون كاش", PocketKind.MOBILE_WALLET, color = sky),
        PocketEntity(3, "بنك مصر الأهلي", PocketKind.BANK, color = turquoise),
    )

    private fun sub(id: Long, name: String, amount: Long, shared: Int = 1, trialEnd: LocalDate? = null, yearly: Long? = null, daysTo: Long = 20) =
        SubRowUi(
            SubscriptionView(
                item = RecurringEntity(id, name, amount, 1, today.dayOfMonth, today.plusDays(daysTo), kind = RecurringKind.SUBSCRIPTION, sharedWith = shared, trialEnd = trialEnd, yearlyPrice = yearly),
                monthly = amount, perYear = amount * 12, userShare = amount / shared, othersOwe = amount - amount / shared,
                trialDaysLeft = trialEnd?.let { 2L }, stopSaving = 120_000, yearlySaving = yearly?.let { amount * 12 - it },
            ),
            daysTo,
        )

    private val subsUi = SubscriptionsUi(
        loading = false,
        rows = listOf(sub(1, "نتفلكس", 22_000, 4, daysTo = 3), sub(2, "سبوتيفاي", 9_000, yearly = 90_000), sub(3, "شاهد", 15_000, trialEnd = today.plusDays(2), daysTo = 2)),
        monthlyTotal = 46_000, perYear = 552_000, tickets = 3,
        trial = TrialCardUi(sub(3, "شاهد", 15_000, trialEnd = today.plusDays(2), daysTo = 2).view, false),
        question = QuestionCardUi(sub(2, "سبوتيفاي", 9_000).view, 2),
        chips = listOf("يوتيوب بريميوم", "أنغامي", "ChatGPT", "حاجة تانية"),
    )

    private fun subs(ui: SubscriptionsUi) {
        rule.audit { SubscriptionsContent(ui, {}, {}, {}, {}, {}, { _, _ -> }, {}) }.assertNone()
    }

    @Test fun subscriptions() = subs(subsUi)
    @Test fun subscriptionsRemindedNoQuestion() = subs(subsUi.copy(trial = subsUi.trial!!.copy(reminded = true), question = null))
    @Test fun subscriptionsEmpty() = subs(SubscriptionsUi(loading = false, chips = listOf("نتفلكس", "حاجة تانية")))
    @Test fun subscriptionsLoading() = subs(SubscriptionsUi())

    @Test fun subscriptionFormNew() {
        val f = SubscriptionForm(loaded = true, pockets = pockets)
        rule.audit { SubscriptionFormContent(f, false, {}, SubscriptionFormActions.None) }.assertNone()
    }
    @Test fun subscriptionFormFilled() {
        val f = SubscriptionForm(
            loaded = true, name = "نتفلكس", amount = AmountInput("220"), day = 15, yearlyPrice = AmountInput("2000"), sharedWith = 4, trialDays = 7, pocketId = 2,
            pockets = pockets, failed = true,
        )
        rule.audit { SubscriptionFormContent(f, true, {}, SubscriptionFormActions.None) }.assertNone()
    }
    @Test fun subscriptionFormYearly() {
        val f = SubscriptionForm(loaded = true, name = "دومين", yearly = true, amount = AmountInput("900"), pocketId = 1, pockets = pockets)
        rule.audit { SubscriptionFormContent(f, false, {}, SubscriptionFormActions.None) }.assertNone()
    }

    // ---- fixed costs

    private fun row(id: Long, name: String, amount: Long, kind: RecurringKind = RecurringKind.BILL) =
        RecurringRow(id, name, amount, kind, 2, "فودافون كاش", today.plusDays(id * 3), null)

    private val fixedUi = FixedCostsUi(
        loading = false,
        items = listOf(
            FixedCostUi(row(1, "إيجار الشقة", 400_000), DueState.Due(today.plusDays(3))),
            FixedCostUi(row(2, "كهربا", 60_000), DueState.Overdue),
            FixedCostUi(row(3, "النت", 40_000), DueState.Paid),
        ),
        kids = row(4, "مصروف الولاد", 80_000, RecurringKind.KIDS),
    )

    private fun fixed(ui: FixedCostsUi) {
        rule.audit { FixedCostsContent(ui, {}, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun fixedCosts() = fixed(fixedUi)
    @Test fun fixedCostsOnlyKids() = fixed(fixedUi.copy(items = emptyList()))
    @Test fun fixedCostsEmpty() = fixed(FixedCostsUi(loading = false))
    @Test fun fixedCostsLoading() = fixed(FixedCostsUi())

    @Test fun fixedCostFormNew() {
        rule.audit { FixedCostFormContent(FixedCostForm(loaded = true, pockets = pockets), false, {}, FixedCostFormActions.None) }.assertNone()
    }
    @Test fun fixedCostFormFilled() {
        val f = FixedCostForm(loaded = true, name = "إيجار الشقة", amount = AmountInput("4000"), day = 28, pocketId = 3, kind = RecurringKind.INSTALLMENT, pockets = pockets, failed = true)
        rule.audit { FixedCostFormContent(f, true, {}, FixedCostFormActions.None) }.assertNone()
    }
}
