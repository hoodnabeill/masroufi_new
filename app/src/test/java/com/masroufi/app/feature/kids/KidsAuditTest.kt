package com.masroufi.app.feature.kids

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.ChildAdvanceEntity
import com.masroufi.app.data.db.ChildEntity
import com.masroufi.app.data.db.ChoreEntity
import com.masroufi.app.data.repo.ChildHandover
import com.masroufi.app.data.repo.ChildJar
import com.masroufi.app.data.repo.ChildView
import com.masroufi.app.data.repo.ChoreView
import com.masroufi.app.data.repo.KidsView
import com.masroufi.app.feature.add.AmountInput
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate

/** The kids screen, its sheets and the child form at 200% font, right-to-left, animations off (Task 62). No `allowOverflow`. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class KidsAuditTest {
    @get:Rule val rule = createComposeRule()

    private val none = object : KidsActions {}

    private fun child(id: Long, name: String, color: Int) = ChildEntity(id = id, name = name, birthYear = 2018, color = color, weekly = 10_000)

    private fun view(c: ChildEntity, editable: Boolean = true, jar: ChildJar? = null, advance: ChildAdvanceEntity? = null) = ChildView(
        child = c, editable = editable, age = 8, left = 6_500, handedOver = 10_000, daysIntoWeek = 3, jar = jar, waitingAdvance = advance,
        handover = ChildHandover(c.id, 10_000, 0, 0, 0), punches = 3, lastSpendAt = null,
    )

    private val reem = child(1, "ريم", 0xFF3DD6C8.toInt())
    private val yassin = child(2, "ياسين", 0xFFF2C94C.toInt())
    private val jar = ChildJar("العجلة", 150_000, 12_000, 8, false, 5_000, 1_000)
    private val advance = ChildAdvanceEntity(id = 1, childId = 1, amount = 2_000, requestedAt = 0, weekStart = LocalDate.of(2026, 11, 7))

    private val kidsView = KidsView(
        children = listOf(view(reem, jar = jar, advance = advance), view(yassin, editable = false)),
        chores = listOf(ChoreView(ChoreEntity(1, "ترتيب الأوضة", 2_000, 1), 1), ChoreView(ChoreEntity(2, "غسيل المواعين", 3_000, null), null)),
        handoverDay = DayOfWeek.SATURDAY, nextHandover = LocalDate.of(2026, 11, 14), total = 20_000, monthEstimate = 80_000, envelopeName = "العيال", canAddChild = false,
    )

    private val ui = KidsUi(
        loading = false, view = kidsView,
        tickets = kidsView.children.map { ChildTicketUi(it, DayOfWeek.WEDNESDAY, 5) }, notice = KidsNotice.CHORE_TAKEN,
    )

    private fun kids(u: KidsUi) {
        rule.audit { KidsContent(u, none, {}, {}, {}) }.assertNone()
    }

    @Test fun kids() = kids(ui)
    @Test fun kidsEmpty() = kids(KidsUi(loading = false, view = kidsView.copy(children = emptyList(), chores = emptyList(), total = 0, canAddChild = true)))
    @Test fun kidsLoading() = kids(KidsUi())
    @Test fun handoverDaySheet() = kids(ui.copy(sheet = KidsSheet.HandoverDay))
    @Test fun addChoreSheet() = kids(ui.copy(sheet = KidsSheet.AddChore))
    @Test fun chooseChildSheet() = kids(ui.copy(sheet = KidsSheet.ChooseChild(2)))
    @Test fun plusSheet() = kids(ui.copy(sheet = KidsSheet.Plus))
    @Test fun pinSetupSheet() = kids(ui.copy(pinSetup = PinSetup(1, PinStage.REPEAT, "12", "1234", mismatch = true)))

    // ---- child form

    private val formNone = object : ChildFormActions {}
    private val form = ChildForm(
        loaded = true, name = "ريم", birthYear = 2018, colorIndex = 1, weekly = AmountInput("100"), jar = true, jarLabel = "العجلة", jarTarget = AmountInput("1500"),
        match = true, matchPer = AmountInput("50"), matchAdd = AmountInput("10"),
    )

    private fun form(f: ChildForm, edit: Boolean = false) {
        rule.audit { ChildFormContent(f, if (edit) object : ChildFormActions { override val isEdit = true } else formNone, {}) }.assertNone()
    }

    @Test fun childFormNew() = form(ChildForm(loaded = true, birthYear = 2018))
    @Test fun childFormFull() = form(form)
    @Test fun childFormEdit() = form(form.copy(failed = true), edit = true)
}
