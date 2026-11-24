package com.masroufi.app.feature.add

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.CategoryEntity
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private object AuditActions : AddSheetActions {
    override fun setType(type: EntryKind) = Unit
    override fun digit(d: Int) = Unit
    override fun point() = Unit
    override fun backspace() = Unit
    override fun focusFee(on: Boolean) = Unit
    override fun selectCategory(id: Long) = Unit
    override fun selectPocket(id: Long) = Unit
    override fun selectToPocket(id: Long) = Unit
    override fun setNote(text: String) = Unit
    override fun setRoundUp(on: Boolean) = Unit
}

/** The add sheet at 200% font, right-to-left, animations off (Task 60). A tall screen: the sheet is taller than the default one. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddSheetAuditTest {
    @get:Rule val rule = createComposeRule()

    private val cash = PocketEntity(id = 1, name = "الكاش", kind = PocketKind.CASH, color = 0)
    private val wallet = PocketEntity(id = 2, name = "فودافون كاش", kind = PocketKind.MOBILE_WALLET, color = 0)
    private val cats = listOf(
        CategoryEntity(1, "أكل", "grid", 0), CategoryEntity(2, "مواصلات", "grid", 0), CategoryEntity(3, "فواتير", "grid", 0),
    )
    private val today = DayTicketEntity(date = LocalDate.of(2026, 11, 12), amount = 15_600, base = 15_600)

    private fun form(type: EntryKind, amount: String, extra: (AddForm) -> AddForm = { it }) =
        extra(AddForm(type = type, amount = AmountInput(amount), pocketId = cash.id, toPocketId = wallet.id, pockets = listOf(cash, wallet), categories = cats, categoryId = 1))

    private fun audit(state: AddUiState) {
        rule.audit { Column { AddSheetContent(state, AuditActions, onClose = {}, onSave = {}) } }.assertNone()
    }

    @Test fun spentWithRoundUpAndItem() = audit(AddUiState(form(EntryKind.SPENT, "25") { it.copy(item = "شاي", unitPrice = "12", note = "الصبح") }, today, plus = true, roundUpStep = 10))
    @Test fun spentEmpty() = audit(AddUiState(form(EntryKind.SPENT, ""), today))
    @Test fun received() = audit(AddUiState(form(EntryKind.RECEIVED, "500"), today))
    @Test fun moved() = audit(AddUiState(form(EntryKind.MOVED, "300") { it.copy(fee = AmountInput("5"), feeFocused = true) }, today))
    @Test fun editMode() = audit(AddUiState(form(EntryKind.SPENT, "40") { it.copy(edit = EditTarget(7, 0L, null, 0L, 4_000L)) }, today))
}
