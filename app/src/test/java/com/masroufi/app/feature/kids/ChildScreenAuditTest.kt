package com.masroufi.app.feature.kids

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.ChildAdvanceEntity
import com.masroufi.app.data.db.ChildEntity
import com.masroufi.app.data.repo.ChildHandover
import com.masroufi.app.data.repo.ChildJar
import com.masroufi.app.data.repo.ChildView
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/** The child screen (and its PIN pad) at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChildScreenAuditTest {
    @get:Rule val rule = createComposeRule()

    private val entity = ChildEntity(id = 1, name = "ريم", birthYear = 2018, color = 0xFF3DD6C8.toInt(), weekly = 10_000)

    private fun view(left: Long = 6_500, jar: ChildJar? = null, advance: ChildAdvanceEntity? = null) = ChildView(
        child = entity, editable = true, age = 8, left = left, handedOver = 10_000, daysIntoWeek = 3, jar = jar, waitingAdvance = advance,
        handover = ChildHandover(1, 10_000, 0, 0, 0), punches = 3, lastSpendAt = null,
    )

    private val jar = ChildJar("العجلة", 150_000, 12_000, 8, false, 5_000, 1_000)

    private fun audit(ui: ChildUi) {
        rule.audit { ChildContent(ui, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun ticketOnly() = audit(ChildUi(loading = false, child = view()))
    @Test fun withJarAndAWaitingAdvance() = audit(
        ChildUi(
            loading = false, child = view(jar = jar, advance = ChildAdvanceEntity(childId = 1, amount = 2_000, requestedAt = 0, weekStart = LocalDate.of(2026, 11, 7))),
            notice = ChildNotice.AdvanceSent,
        ),
    )
    @Test fun moneyUsedUp() = audit(ChildUi(loading = false, child = view(left = 0, jar = jar.copy(target = null, label = null, matchPer = null, matchAdd = null)), notice = ChildNotice.AskParent(1_000)))

    private fun pad(wait: Boolean) {
        rule.audit {
            Column(Modifier.verticalScroll(rememberScrollState())) { PinPad(2, enabled = !wait, okEnabled = !wait, onDigit = {}, onBackspace = {}, onOk = {}, fingerprint = {}) }
        }.assertNone()
    }

    @Test fun pinPad() = pad(false)
    @Test fun pinPadWaiting() = pad(true)
}
