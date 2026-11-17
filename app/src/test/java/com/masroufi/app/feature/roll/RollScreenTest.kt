package com.masroufi.app.feature.roll

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.repo.DayKind
import com.masroufi.app.data.repo.RollDay
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.TicketStatus
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RollScreenTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 12)

    private fun stub(date: LocalDate, spent: Long, count: Int, status: TicketStatus, destination: LeftoverDestination? = null) {
        val ticket = DayTicketEntity(date = date, amount = 24_000, base = 24_000, spent = spent, entryCount = count, status = status, destination = destination)
        rule.setContent {
            MasroufiTheme { DayStub(RollDay(date, ticket, emptyList(), DayKind.TICKET), today, false, emptyMap(), {}, {}) }
        }
    }

    @Test fun todayLine() {
        stub(today, 8_400, 3, TicketStatus.OPEN)
        rule.onNodeWithText("النهارده · صرفت").assertExists()
        rule.onNodeWithText("84 من 240").assertExists()
        rule.onNodeWithText("مفتوحة").assertExists()
    }

    @Test fun underLine() {
        stub(today.minusDays(1), 23_500, 5, TicketStatus.CLOSED_BY_HAND, LeftoverDestination.JAR)
        rule.onNodeWithText("صرفت 235 في 5 مرات").assertExists()
        rule.onNodeWithText("تحت التذكرة بـ 5 · راحوا الحصّالة").assertExists()
    }

    @Test fun overLine() {
        stub(today.minusDays(3), 27_500, 4, TicketStatus.CLOSED_AUTO)
        rule.onNodeWithText("صرفت 275 في 4 مرات").assertExists()
        rule.onNodeWithText("فوق التذكرة بـ 35 · اتوزّعوا على باقي الأيام").assertExists()
    }

    @Test fun searchHighlightUsesTheQuickLogNormalising() {
        assertEquals(6..9, matchRange("شاي و قهوة", "قهوه"))
        assertEquals(0..2, matchRange("أكلة", "اكل"))
        assertNull(matchRange("بنزين", "قهوه"))
    }
}
