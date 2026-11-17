package com.masroufi.app.feature.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.RecurringEntity
import com.masroufi.app.data.db.RecurringKind
import com.masroufi.app.data.repo.subscriptionView
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SubscriptionRowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sharedRowShowsTheSplitAndWhatFriendsOwe() {
        val today = LocalDate.of(2026, 11, 1)
        val item = RecurringEntity(
            id = 1, name = "نتفليكس", amount = 22_000, pocketId = 1, scheduleDay = 23, nextDue = LocalDate.of(2026, 11, 23),
            kind = RecurringKind.SUBSCRIPTION, sharedWith = 4,
        )
        rule.setContent { MasroufiTheme { SubscriptionRow(SubRowUi(subscriptionView(item, today), 22), {}, {}) } }
        rule.onNodeWithText("يوم 23 · مقسومة على 4").assertExists()
        rule.onNodeWithText("نصيبك 55 · ليك 165 عند 3 صحاب").assertExists()
        rule.onNodeWithText("فكّرهم").assertExists()
    }
}
