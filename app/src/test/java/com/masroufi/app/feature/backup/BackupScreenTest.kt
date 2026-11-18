package com.masroufi.app.feature.backup

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime

private class FakeBackup : BackupActions {
    val calls = mutableListOf<String>()
    override fun backupNow() { calls += "backup" }
    override fun restoreTapped() { calls += "restore" }
    override fun setAuto(on: Boolean) { calls += "auto:$on" }
    override fun pickFolder() { calls += "folder" }
    override fun changePassword() { calls += "password" }
    override fun exportTapped() { calls += "export" }
    override fun closeSheet() { calls += "close" }
    override fun submitPassword(password: String, repeat: String) { calls += "submit:$password:$repeat" }
    override fun confirmRestore(password: String) { calls += "confirm:$password" }
    override fun confirmExport() { calls += "confirmExport" }
    override fun launched() {}
    override fun created(uri: Uri?) {}
    override fun exported(uri: Uri?) {}
    override fun opened(uri: Uri?) {}
    override fun treePicked(uri: Uri?) {}
}

/** Today is 2026-11-18. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupScreenTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val never = BackupUi(loading = false, today = today)
    private val last = never.copy(lastDay = today.minusDays(1), lastTime = LocalTime.of(21, 12), tickets = 412, bytes = 1_800_000)

    private fun show(ui: BackupUi, actions: BackupActions = FakeBackup()) {
        rule.setContent { MasroufiTheme { BackupContent(ui, actions, onBack = {}) } }
    }

    @Test fun neverBackedUpShowsTheYellowStateAndTheFirstLine() {
        show(never)
        rule.onNodeWithText("لسه مفيش نسخة. دقيقة واحدة وتذاكرك في أمان.").assertExists()
        rule.onNodeWithText("اعمل نسخة دلوقتي").assertExists()
    }

    @Test fun theHeroShowsTheLastBackupItsTicketsSizeAndEncryption() {
        show(last)
        rule.onNodeWithText("آخر نسخة").assertExists()
        rule.onNodeWithText("امبارح 9:12 م").assertExists()
        rule.onNodeWithText("412 تذكرة · 1.8 ميجا · متشفّرة").assertExists()
    }

    @Test fun anOlderBackupShowsItsDate() {
        show(last.copy(lastDay = LocalDate.of(2026, 11, 3)))
        rule.onNodeWithText("3 نوفمبر 9:12 م").assertExists()
    }

    @Test fun aFailedAutomaticRunShowsOneQuietLineAndKeepsTheLastBackup() {
        show(last.copy(failed = true))
        rule.onNodeWithText("آخر نسخة فشلت. جرّب تاني.").assertExists()
        rule.onNodeWithText("امبارح 9:12 م").assertExists()
    }

    @Test fun whileWritingTheButtonBecomesTheProgressBar() {
        show(last.copy(progress = 0.5f))
        rule.onNodeWithText("بنحفظ النسخة…").assertExists()
        rule.onNodeWithText("اعمل نسخة دلوقتي").assertDoesNotExist()
    }

    @Test fun theMainButtonsAndRowsAct() {
        val a = FakeBackup()
        show(last, a)
        rule.onNodeWithText("اعمل نسخة دلوقتي").performClick()
        rule.onNodeWithText("رجّع من نسخة").performClick()
        rule.onNodeWithContentDescription("نسخة لوحدها كل أسبوع").performScrollTo().performClick()
        rule.onNodeWithText("اختار فولدر").performScrollTo().performClick()
        rule.onNodeWithText("اضبطها").performScrollTo().performClick()
        rule.onNodeWithText("صدّر لإكسل").performScrollTo().performClick()
        assertEquals(listOf("backup", "restore", "auto:true", "folder", "password", "export"), a.calls)
    }

    @Test fun theWarningAndTheNewPhoneStepsAreThere() {
        show(never)
        rule.onNodeWithText("احفظ كلمة السر كويس").performScrollTo().assertExists()
        rule.onNodeWithText("جبت موبايل جديد؟").performScrollTo().assertExists()
        rule.onNodeWithText("اكتب كلمة السر. تذاكرك وحصّالتك وألبومك يرجعوا زي ما هم.").performScrollTo().assertExists()
    }

    @Test fun theRestoreSheetNamesTheBackupDateAndWhatItReplaces() {
        show(last.copy(sheet = BackupSheet.Restore, restore = RestoreInfo(LocalDate.of(2026, 11, 3), 412)))
        rule.onNodeWithText("نسخة 3 نوفمبر 2026 · 412 تذكرة. هتحل مكان كل اللي على الموبايل.").assertExists()
    }

    @Test fun theExportSheetSaysItIsNotEncrypted() {
        show(last.copy(sheet = BackupSheet.Export))
        rule.onNodeWithText("الملف ده مش متشفّر. أي حد يفتحه هيشوف كل حركاتك. احفظه في مكان تثق فيه.").assertExists()
    }

    @Test fun aWrongPasswordIsOneLineInTheRestoreSheet() {
        show(last.copy(sheet = BackupSheet.Restore, restore = RestoreInfo(today, 1), message = BackupMessage.WrongPassword))
        rule.onNodeWithText("كلمة السر غلط. مفيش حاجة اتغيّرت.").assertExists()
    }
}
