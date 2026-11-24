package com.masroufi.app.ui.audit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.ui.components.ChipStyle
import com.masroufi.app.ui.components.PlusLocked
import com.masroufi.app.ui.components.PlusSheetContent
import com.masroufi.app.ui.components.PrimaryButton
import com.masroufi.app.ui.components.SecondaryButton
import com.masroufi.app.ui.components.TextLink
import com.masroufi.app.ui.components.TzChip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The harness on its own composables (a good one and a bad one), then on the shared components every screen uses. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AuditHarnessTest {
    @get:Rule val rule = createComposeRule()

    @Test fun aKnownGoodComposableHasNoFindings() {
        val findings = rule.audit {
            Column {
                Box(Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) {}, contentAlignment = Alignment.Center) { Text("حفظ") }
                Text("سطر قصير")
            }
        }
        findings.assertNone()
    }

    @Test fun aKnownBadComposableIsCaughtOnEveryRule() {
        val findings = rule.audit {
            Column(Modifier.width(120.dp)) {
                // 20 dp, nothing to read
                Box(Modifier.size(20.dp).clickable {})
                // one line in 40 dp
                Text("a very long single line of text that cannot fit", Modifier.width(40.dp), maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
        println(findings.joinToString("\n"))
        assertEquals(setOf(AuditRule.TOUCH_TARGET, AuditRule.LABEL, AuditRule.OVERFLOW), findings.map { it.rule }.toSet())
        assertTrue("the description names the node", findings.first { it.rule == AuditRule.OVERFLOW }.node.contains("a very long"))
        runCatching { findings.assertNone() }.let { assertTrue("assertNone lists them", it.exceptionOrNull()?.message?.contains("TOUCH_TARGET") == true) }
    }

    @Test fun textThatIsMeantToBeCutCanBeAllowed() {
        val findings = rule.audit(AuditConfig(allowOverflow = listOf("a very long"))) {
            Text("a very long single line of text that cannot fit", Modifier.width(40.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        findings.assertNone()
    }

    // Shared components (Task 59): fixes here help every screen.

    @Test fun primaryAndSecondaryButtonsPass() {
        rule.audit {
            Column {
                PrimaryButton("احفظ التذكرة دلوقتي", {})
                PrimaryButton("احفظ", {}, enabled = false)
                SecondaryButton("مش دلوقتي", {})
                TextLink("اعرف أكتر", {})
            }
        }.assertNone()
    }

    @Test fun chipsPass() {
        rule.audit {
            Column {
                Row { TzChip("جنيه", true, {}); TzChip("خمسة", false, {}) }
                Row { TzChip("الكل", true, {}, style = ChipStyle.Filter); TzChip("الأكل", false, {}, style = ChipStyle.Filter) }
            }
        }.assertNone()
    }

    // The test screen is 470 dp tall: the cards sit in a scrolling column so they get the height they would on a real screen.
    @Test fun thePlusCardPasses() {
        rule.audit {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                PlusLocked(R.string.env_plus_line, PlusFeature.ENVELOPES) { Text("محتوى") }
                PlusSheetContent(R.string.env_plus_line, PlusFeature.ENVELOPES) {}
            }
        }.assertNone()
    }
}
