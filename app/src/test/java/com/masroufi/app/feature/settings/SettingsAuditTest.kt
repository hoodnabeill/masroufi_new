package com.masroufi.app.feature.settings

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.core.Payday
import com.masroufi.app.domain.LeftoverDestination
import com.masroufi.app.domain.NotifKind
import com.masroufi.app.entitlements.PlusPlan
import com.masroufi.app.entitlements.RestoreOutcome
import com.masroufi.app.feature.backup.BackupActions
import com.masroufi.app.feature.backup.BackupContent
import com.masroufi.app.feature.backup.BackupMessage
import com.masroufi.app.feature.backup.BackupSheet
import com.masroufi.app.feature.backup.BackupUi
import com.masroufi.app.feature.backup.RestoreInfo
import com.masroufi.app.feature.lock.LockError
import com.masroufi.app.feature.lock.LockSettingsActions
import com.masroufi.app.feature.lock.LockSettingsContent
import com.masroufi.app.feature.lock.LockSettingsUi
import com.masroufi.app.feature.lock.LockStep
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private class NoSettings : SettingsActions {
    override fun open(sheet: SettingsSheet) {}
    override fun closeSheet() {}
    override fun setPayday(v: Payday) {}
    override fun setReminderHour(v: Int?) {}
    override fun setLeftover(v: LeftoverDestination) {}
    override fun setRoundUpStep(v: Int) {}
    override fun setVibration(v: Boolean) {}
    override fun setTicketSounds(v: Boolean) {}
    override fun setHideAmounts(v: Boolean) {}
    override fun setLanguage(tag: String) {}
    override fun manageUrl() = ""
    override fun restore() {}
}

private class NoBackup : BackupActions {
    override fun backupNow() {}
    override fun restoreTapped() {}
    override fun setAuto(on: Boolean) {}
    override fun pickFolder() {}
    override fun changePassword() {}
    override fun exportTapped() {}
    override fun closeSheet() {}
    override fun submitPassword(password: String, repeat: String) {}
    override fun confirmRestore(password: String) {}
    override fun confirmExport() {}
    override fun launched() {}
    override fun created(uri: Uri?) {}
    override fun exported(uri: Uri?) {}
    override fun opened(uri: Uri?) {}
    override fun treePicked(uri: Uri?) {}
}

private class NoNotifications : NotificationActions {
    override fun setKind(kind: NotifKind, on: Boolean) {}
    override fun permissionResult(kind: NotifKind?, granted: Boolean) {}
    override fun openReminder() {}
    override fun closeReminder() {}
    override fun setReminderHour(hour: Int?) {}
}

/** Settings, notifications, lock, backup, privacy and licences at 200% font, right-to-left, animations off (Task 62). No `allowOverflow`. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAuditTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 18)

    // ---- settings

    private val settings = SettingsUi(
        loading = false, name = "يوسف", level = 4, levelName = "مدبّر", payday = Payday.Day(25), reminderHour = 21, leftover = LeftoverDestination.JAR, roundUpStep = 5,
        isPlus = true, vibration = true, ticketSounds = false, hideAmounts = false, lockOn = true, plan = PlusPlan.YEARLY, renewsOn = LocalDate.of(2027, 3, 1),
        restore = RestoreOutcome.FOUND, lastBackup = LocalDateTime.of(2026, 11, 17, 21, 12), today = today,
    )

    private fun settings(ui: SettingsUi, email: String = "help@example.com") {
        rule.audit { SettingsContent(ui, NoSettings(), SettingsNav(), email, "1.0.0") }.assertNone()
    }

    @Test fun settingsPlus() = settings(settings)
    @Test fun settingsFreeWithoutBackup() = settings(settings.copy(isPlus = false, plan = null, renewsOn = null, trialEnds = today.plusDays(5), lastBackup = null, restore = null, lockOn = false), email = "")
    @Test fun settingsBackupFailed() = settings(settings.copy(backupFailed = true, payday = Payday.LastDay, reminderHour = null))
    @Test fun settingsLoading() = settings(SettingsUi())
    @Test fun paydaySheet() = settings(settings.copy(sheet = SettingsSheet.Payday))
    @Test fun reminderSheet() = settings(settings.copy(sheet = SettingsSheet.Reminder))
    @Test fun leftoverSheet() = settings(settings.copy(sheet = SettingsSheet.Leftover))
    @Test fun roundUpSheet() = settings(settings.copy(sheet = SettingsSheet.RoundUp))
    @Test fun plusInfoSheet() = settings(settings.copy(sheet = SettingsSheet.PlusInfo))

    // ---- notifications

    private val notifications = NotificationSettingsUi(
        loading = false, on = NotifKind.entries.associateWith { it != NotifKind.MORNING }, reminderHour = 21,
    )

    private fun notifications(ui: NotificationSettingsUi, blocked: Boolean = false) {
        rule.audit { NotificationSettingsContent(ui, NoNotifications(), blocked, {}, {}) }.assertNone()
    }

    @Test fun notificationsAll() = notifications(notifications)
    @Test fun notificationsBlocked() = notifications(notifications.copy(denied = true), blocked = true)
    @Test fun notificationsReminderSheet() = notifications(notifications.copy(sheet = true))
    @Test fun notificationsLoading() = notifications(NotificationSettingsUi())

    // ---- lock settings

    private fun lock(ui: LockSettingsUi) {
        rule.audit { LockSettingsContent(ui, object : LockSettingsActions {}, {}) }.assertNone()
    }

    @Test fun lockOff() = lock(LockSettingsUi(loading = false))
    @Test fun lockOn() = lock(LockSettingsUi(loading = false, lockOn = true))
    @Test fun lockCreateSheet() = lock(LockSettingsUi(loading = false, step = LockStep.Create, digits = "12", error = LockError.Mismatch))
    @Test fun lockVerifySheet() = lock(LockSettingsUi(loading = false, lockOn = true, step = LockStep.VerifyForChange, error = LockError.Wrong, triesLeft = 2))
    @Test fun lockWaitSheet() = lock(LockSettingsUi(loading = false, lockOn = true, step = LockStep.VerifyForOff, error = LockError.Waiting, waitSeconds = 30))

    // ---- backup

    private val never = BackupUi(loading = false, today = today)
    private val last = never.copy(lastDay = today.minusDays(1), lastTime = LocalTime.of(21, 12), tickets = 412, bytes = 1_800_000, auto = true, folder = "Documents/Masroufi", hasPassword = true)

    private fun backup(ui: BackupUi) {
        rule.audit { BackupContent(ui, NoBackup(), {}) }.assertNone()
    }

    @Test fun backupNever() = backup(never)
    @Test fun backupLast() = backup(last.copy(failed = true, message = BackupMessage.ExportSaved))
    @Test fun backupWriting() = backup(last.copy(progress = 0.4f))
    @Test fun backupPasswordSheet() = backup(last.copy(sheet = BackupSheet.SetPassword, message = BackupMessage.PasswordShort))
    @Test fun backupRestoreSheet() = backup(last.copy(sheet = BackupSheet.Restore, restore = RestoreInfo(today.minusDays(3), 380), message = BackupMessage.WrongPassword))
    @Test fun backupExportSheet() = backup(last.copy(sheet = BackupSheet.Export))

    // ---- privacy and licences

    @Test fun privacyAndTerms() {
        rule.audit { LegalScreen({}, "help@example.com") }.assertNone()
    }

    @Test fun licences() {
        rule.audit { LicencesScreen({}) }.assertNone()
    }
}
