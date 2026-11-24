package com.masroufi.app.feature.proverb

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.ProverbCatalog
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The proverb reward at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProverbRewardAuditTest {
    @get:Rule val rule = createComposeRule()

    private fun card(n: Int) = ProverbCatalog.all.first { it.number == n }
    private val jar = ProverbRewardUi.Ready(
        ticketNumber = 47, proverb = card(18), allCollected = false, leftover = 7_200,
        destination = LeftoverDestination.JAR, streak = 13, weekDays = 5,
    )

    private fun audit(s: ProverbRewardUi) {
        rule.audit { ProverbRewardContent(s, {}, {}) }.assertNone()
    }

    @Test fun savedToJar() = audit(jar)
    @Test fun rareCard() = audit(jar.copy(proverb = card(45)))
    @Test fun nothingLeft() = audit(jar.copy(leftover = 0, destination = LeftoverDestination.TOMORROW))
    @Test fun albumComplete() = audit(jar.copy(allCollected = true))
    @Test fun error() = audit(ProverbRewardUi.Error)
}
