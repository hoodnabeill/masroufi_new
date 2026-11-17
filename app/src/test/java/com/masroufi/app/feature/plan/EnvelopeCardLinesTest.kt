package com.masroufi.app.feature.plan

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.EnvelopeKind
import com.masroufi.app.data.repo.EnvelopeLine
import com.masroufi.app.data.repo.EnvelopeView
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
class EnvelopeCardLinesTest {
    @get:Rule val rule = createComposeRule()

    private fun view(sealed: Boolean = false, line: EnvelopeLine? = null, better: Boolean = false) = EnvelopeView(
        id = 1, name = "الخروجات", color = 0, kind = EnvelopeKind.SPENDING, monthly = 90_000, spent = 76_000, left = 14_000, bar = 0.84f,
        sealed = sealed, paidLabelRes = null, line = line, betterThanLast = better, categoryIds = emptySet(),
    )

    private fun show(ui: EnvelopeCardUi) = rule.setContent { MasroufiTheme { PlanEnvelopeCard(ui) } }

    @Test fun runsOutLine() {
        show(EnvelopeCardUi(view(line = EnvelopeLine.RunsOut(LocalDate.of(2026, 11, 16))), CardLine.RunsOut(16)))
        rule.onNodeWithText("الخروجات").assertExists()
        rule.onNodeWithText("فاضل من 900").assertExists()
        rule.onNodeWithText("هيخلص يوم 16 بالمعدل ده").assertExists()
    }

    @Test fun onPaceLine() {
        show(EnvelopeCardUi(view(line = EnvelopeLine.OnPace), CardLine.OnPace))
        rule.onNodeWithText("ماشي على المعدل").assertExists()
    }

    @Test fun betterThanLastLine() {
        show(EnvelopeCardUi(view(line = EnvelopeLine.OnPace, better = true), CardLine.BetterThanLast))
        rule.onNodeWithText("أحسن من الشهر اللي فات").assertExists()
    }

    @Test fun sealedCard() {
        show(EnvelopeCardUi(view(sealed = true), CardLine.Sealed(250_000, 8)))
        rule.onNodeWithText("مدفوع").assertExists()
        rule.onNodeWithText("2,500 · يوم 8").assertExists()
        rule.onNodeWithText("الظرف مقفول لحد الشهر الجاي").assertExists()
    }
}
