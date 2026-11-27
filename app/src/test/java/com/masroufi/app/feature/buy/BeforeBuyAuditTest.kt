package com.masroufi.app.feature.buy

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.WishAnswer
import com.masroufi.app.data.db.WishEntity
import com.masroufi.app.data.repo.WishView
import com.masroufi.app.domain.BeforeBuy
import com.masroufi.app.domain.Installment
import com.masroufi.app.domain.Tenths
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Before you buy, the wish list and the jar question at 200% font, right-to-left, animations off (Task 61). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1400dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BeforeBuyAuditTest {
    @get:Rule val rule = createComposeRule()

    private fun wish(id: Long, name: String, price: Long, answer: WishAnswer, due: Boolean = false, hours: Long = 30, moved: Long? = null) =
        WishView(WishEntity(id, name, price, parkedAt = 1_700_000_000_000, answer = answer, movedToJar = moved), hoursLeft = hours, due = due)

    private val wishes = listOf(
        wish(1, "سماعات بلوتوث", 250_000, WishAnswer.WAITING, hours = 30),
        wish(2, "حذاء جري", 180_000, WishAnswer.WAITING, due = true, hours = 0),
        wish(3, "كتاب", 30_000, WishAnswer.CHANGED_MIND, moved = 30_000),
        wish(4, "شنطة", 120_000, WishAnswer.BOUGHT),
    )

    private val result = BeforeBuy(
        price = 1_200_000, tickets = Tenths(75), workDays = Tenths(35), goldGramsHundredths = 120, seasonSharePercent = 30, seasonName = "رمضان", cashDays = 8,
    )

    private val ui = BeforeBuyUi(
        loading = false, name = "موبايل جديد", price = AmountInput("12000"), monthly = AmountInput("1100"), months = AmountInput("12"), field = null, plus = true,
        result = result, tiles = listOf(BuyTile.TICKETS, BuyTile.WORK_DAYS, BuyTile.GOLD, BuyTile.SEASON),
        installment = Installment(total = 1_320_000, extra = 120_000, perDay = 3_700, months = 12),
        wishes = wishes, changedMindTotal = 30_000,
    )

    private fun audit(s: BeforeBuyUi, ask: WishAsk? = null) {
        rule.audit { BeforeBuyContent(s, ask, {}, BuyActions.None) }.assertNone()
    }

    @Test fun beforeBuy() = audit(ui)
    @Test fun beforeBuyKeypadOnPrice() = audit(ui.copy(field = BuyField.PRICE))
    @Test fun beforeBuyFreeShowsLockedInstallments() = audit(ui.copy(plus = false, installment = null))
    @Test fun beforeBuyOddTiles() = audit(ui.copy(tiles = listOf(BuyTile.TICKETS, BuyTile.WORK_DAYS, BuyTile.GOLD), installment = null, monthly = AmountInput(), months = AmountInput()))
    @Test fun beforeBuyNothingTypedEmptyList() = audit(BeforeBuyUi(loading = false, field = null))
    @Test fun beforeBuyLoading() = audit(BeforeBuyUi(field = null))
    @Test fun jarQuestionSheet() = audit(ui, WishAsk(2, "حذاء جري", 180_000, 180_000))
    @Test fun jarQuestionSheetCapped() = audit(ui, WishAsk(2, "حذاء جري", 180_000, 90_000))
}
