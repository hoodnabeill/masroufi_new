package com.masroufi.app.feature.lock

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.audit.assertNone
import com.masroufi.app.ui.audit.audit
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The lock screen at 200% font, right-to-left, animations off (Task 60). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar-w320dp-h1000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LockAuditTest {
    @get:Rule val rule = createComposeRule()

    private fun audit(ui: LockUi, fingerprint: Boolean = true) {
        rule.audit { LockContent(ui, fingerprint, {}, {}, {}, {}) }.assertNone()
    }

    @Test fun pinWithFingerprint() = audit(LockUi(loaded = true, digits = "12"))
    @Test fun pinOnly() = audit(LockUi(loaded = true, pinLength = 6), fingerprint = false)
    @Test fun wrongPin() = audit(LockUi(loaded = true, wrong = true, shake = 1))
    @Test fun waiting() = audit(LockUi(loaded = true, waitMillis = 90_000))
    @Test fun unreadablePin() = audit(LockUi(loaded = true, unreadable = true))
}
