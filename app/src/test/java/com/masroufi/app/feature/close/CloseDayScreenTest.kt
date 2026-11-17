package com.masroufi.app.feature.close

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.repo.BiggestPunch
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloseDayScreenTest {
    @get:Rule val rule = createComposeRule()

    private val under = CloseDayUi.Ready(
        date = LocalDate.of(2026, 11, 12), ticket = 24_000, spent = 16_800, holes = 6, biggest = BiggestPunch("بقالة", 9_000),
        leftover = 7_200, over = 0, spreadDays = 12, spreadPerDay = 0, jarAfter = 191_200, emergency = 60_000,
        tomorrowWith = 31_200, tomorrowWithout = 24_000, defaultDestination = LeftoverDestination.JAR,
    )

    private fun show(s: CloseDayUi.Ready, torn: MutableList<LeftoverDestination> = mutableListOf()) =
        rule.setContent {
            MasroufiTheme { CloseDayContent(s, TearState.Idle, { torn += it }, {}, {}) }
        }

    @Test fun mockCopyAndSelection() {
        show(under)
        rule.onNodeWithText("يومك خلص على خير").assertExists()
        rule.onNodeWithText("تذكرة الخميس").assertExists()
        rule.onNodeWithText("صرفت 168 في 6 مرات · أكبر حاجة: بقالة 90").assertExists()
        rule.onNodeWithText("كعب التذكرة", substring = true).assertExists()
        rule.onNodeWithText("الـ 72 يروحوا فين؟").assertExists()
        rule.onNodeWithText("تبقى 1,912", substring = true).assertExists()
        rule.onNodeWithText("الجمعة تبقى 312 بدل 240", substring = true).assertExists()
        rule.onNodeWithText("فيه 600 دلوقتي", substring = true).assertExists()
        rule.onNodeWithText("الحصّالة", substring = true).assertIsSelected()
    }

    @Test fun buttonTearsToTheChosenCard() {
        val torn = mutableListOf<LeftoverDestination>()
        show(under, torn)
        rule.onNodeWithText("ظرف الطوارئ", substring = true).performClick()
        rule.onNodeWithText("اقطع التذكرة").performClick()
        rule.onNodeWithText("اقطع التذكرة").performClick() // a second tap is ignored
        assertEquals(listOf(LeftoverDestination.EMERGENCY), torn)
    }

    @Test fun zeroLeftoverHidesDestinations() {
        show(under.copy(spent = 24_000, leftover = 0))
        rule.onNodeWithText("قفلتها على الشعرة. شاطر.").assertExists()
        rule.onNodeWithText("ظرف الطوارئ", substring = true).assertDoesNotExist()
        rule.onNodeWithText("اقطع التذكرة").assertExists()
    }

    @Test fun overCopy() {
        show(under.copy(spent = 27_500, leftover = 0, over = 3_500, spreadPerDay = 300))
        rule.onNodeWithText("عدّيت بـ 35. ولا يهمك، هوزّعهم على الـ 12 يوم الجايين (3 جنيه في اليوم).").assertExists()
        rule.onNodeWithText("اقطعها وكمّل").assertExists()
    }

    @Test fun overOnLastDay() {
        show(under.copy(spent = 27_500, leftover = 0, over = 3_500, spreadDays = 0))
        rule.onNodeWithText("عدّيت بـ 35.").assertExists()
    }
}
