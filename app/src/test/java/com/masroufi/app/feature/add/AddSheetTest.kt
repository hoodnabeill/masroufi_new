package com.masroufi.app.feature.add

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.data.db.DayTicketEntity
import com.masroufi.app.data.db.PocketEntity
import com.masroufi.app.data.db.PocketKind
import com.masroufi.app.domain.EntryKind
import com.masroufi.app.ui.haptics.HapticLevel
import com.masroufi.app.ui.haptics.Haptics
import com.masroufi.app.ui.haptics.LocalHaptics
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

private class RecordingHaptics : Haptics {
    val levels = mutableListOf<HapticLevel>()
    override fun perform(level: HapticLevel) { levels += level }
}

private object NoActions : AddSheetActions {
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

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddSheetTest {
    @get:Rule val rule = createComposeRule()

    private val cash = PocketEntity(id = 1, name = "الكاش", kind = PocketKind.CASH, color = 0)
    private val today = DayTicketEntity(date = LocalDate.of(2026, 11, 12), amount = 15_600, base = 15_600)

    private fun state(type: EntryKind, amount: String, plus: Boolean = false, step: Int = 0) = AddUiState(
        form = AddForm(type = type, amount = AmountInput(amount), pocketId = cash.id, pockets = listOf(cash)),
        today = today,
        plus = plus,
        roundUpStep = step,
    )

    private fun show(state: AddUiState, haptics: Haptics = RecordingHaptics(), onSave: () -> Unit = {}) {
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalHaptics provides haptics) {
                    Column { AddSheetContent(state, NoActions, onClose = {}, onSave = onSave) }
                }
            }
        }
    }

    @Test fun spentLabelAndLeftLine() {
        show(state(EntryKind.SPENT, "25"))
        rule.onNodeWithText("اخرم التذكرة").assertExists()
        rule.onNodeWithText("هيفضل في تذكرة النهارده 131").assertExists()
    }

    @Test fun incomeLabelAndLine() {
        show(state(EntryKind.RECEIVED, "500"))
        rule.onNodeWithText("ضيف للفلوس").assertExists()
        rule.onNodeWithText("تذكرة بكره هتكبر").assertExists()
        rule.onNodeWithText("اخرم التذكرة").assertDoesNotExist()
    }

    @Test fun zeroAmountRejects() {
        val haptics = RecordingHaptics()
        var saves = 0
        show(state(EntryKind.SPENT, ""), haptics) { saves++ }
        rule.onNodeWithText("اكتب المبلغ").performClick()
        rule.waitForIdle()
        assertEquals("no save without an amount", 0, saves)
        assertEquals(listOf(HapticLevel.Reject), haptics.levels)
    }

    @Test fun roundUpLineOnlyWithPlus() {
        var plus by mutableStateOf(false)
        val line = "قرّبها لـ 30 و 5 جنيه فكة للحصّالة"
        rule.setContent {
            MasroufiTheme { Column { AddSheetContent(state(EntryKind.SPENT, "25", plus = plus, step = 10), NoActions, {}, {}) } }
        }
        rule.onNodeWithText(line).assertDoesNotExist()
        plus = true
        rule.onNodeWithText(line).assertExists()
    }
}
