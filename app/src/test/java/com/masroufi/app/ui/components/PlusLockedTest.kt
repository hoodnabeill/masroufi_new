package com.masroufi.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.R
import com.masroufi.app.data.repo.PlusFeature
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlusLockedTest {
    @get:Rule val rule = createComposeRule()

    @Test fun contentIsHiddenFromAccessibilityAndTheButtonOpensThePaywallWithItsFeature() {
        val opened = mutableListOf<PlusFeature>()
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalOpenPaywall provides { opened += it }) {
                    PlusLocked(R.string.jar_lock_gold, PlusFeature.GOLD) { Text("secret content") }
                }
            }
        }
        rule.onNodeWithText("secret content").assertDoesNotExist()
        rule.onNodeWithText("الدهب بالسعر اللي تكتبه، مع بلس.").assertExists()
        rule.onNodeWithText("جرّب بلس").performClick()
        assertEquals(listOf(PlusFeature.GOLD), opened)
    }

    @Test fun theButtonIsLeftOutWhileEndOfMonthModeIsOn() {
        val opened = mutableListOf<PlusFeature>()
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalEndOfMonthMode provides true, LocalOpenPaywall provides { opened += it }) {
                    PlusLocked(R.string.jar_lock_gold, PlusFeature.GOLD) { Text("secret content") }
                }
            }
        }
        rule.onNodeWithText("الدهب بالسعر اللي تكتبه، مع بلس.").assertExists()
        rule.onNodeWithText("جرّب بلس").assertDoesNotExist()
        assertTrue(opened.isEmpty())
    }

    @Test fun theLimitSheetClosesItselfThenOpensThePaywallWithItsFeature() {
        val log = mutableListOf<String>()
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalOpenPaywall provides { log += "paywall:${it.name}" }) {
                    PlusSheetContent(R.string.env_plus_line, PlusFeature.ENVELOPES) { log += "dismiss" }
                }
            }
        }
        rule.onNodeWithText("الظروف من غير حد مع بلس.").assertExists()
        rule.onNodeWithText("جرّب بلس").performClick()
        assertEquals(listOf("dismiss", "paywall:ENVELOPES"), log)
    }

    @Test fun theLimitSheetHidesItsButtonInEndOfMonthModeAndOnlyCloses() {
        val log = mutableListOf<String>()
        rule.setContent {
            MasroufiTheme {
                CompositionLocalProvider(LocalEndOfMonthMode provides true, LocalOpenPaywall provides { log += "paywall:${it.name}" }) {
                    PlusSheetContent(R.string.env_plus_line, PlusFeature.ENVELOPES) { log += "dismiss" }
                }
            }
        }
        rule.onNodeWithText("جرّب بلس").assertDoesNotExist()
        rule.onNodeWithText("تمام").performClick()
        assertEquals(listOf("dismiss"), log)
    }

    /**
     * Every Plus entry point names its feature at the call site (compiler-checked parameters) and none still shows the
     * "coming soon" placeholder. A source scan: the screens need their ViewModels to compose, so this guards the mapping.
     */
    @Test fun everyEntryPointNamesItsFeature() {
        val root = File("src/main/java/com/masroufi/app")
        assertTrue("run from the app module", root.isDirectory)
        val expected = mapOf(
            "feature/plan/JarSection.kt" to listOf("PlusFeature.GOLD", "PlusFeature.ROUND_UPS"),
            "feature/plan/SeasonsSection.kt" to listOf("PlusFeature.SEASONS"),
            "feature/plan/ZakatScreen.kt" to listOf("PlusFeature.ZAKAT"),
            "feature/plan/PlanScreen.kt" to listOf("PlusFeature.ENVELOPES"),
            "feature/plan/EnvelopeFormScreen.kt" to listOf("PlusFeature.ENVELOPES"),
            "feature/people/DutyLedgerScreen.kt" to listOf("PlusFeature.DUTY"),
            "feature/people/PeopleScreen.kt" to listOf("PlusFeature.DUTY"),
            "feature/people/GameyaScreen.kt" to listOf("PlusFeature.GAMEYA"),
            "feature/people/GameyaFormScreen.kt" to listOf("PlusFeature.GAMEYA"),
            "feature/kids/KidsScreen.kt" to listOf("PlusFeature.KIDS"),
            "feature/match/MatchScreen.kt" to listOf("PlusFeature.MATCH_HISTORY"),
            "feature/basket/BasketScreen.kt" to listOf("PlusFeature.BASKET"),
            "feature/buy/BeforeBuyScreen.kt" to listOf("PlusFeature.INSTALLMENT_COMPARE"),
        )
        for ((path, features) in expected) {
            val text = File(root, path).readText()
            features.forEach { assertTrue("$path should name $it", text.contains(it)) }
        }
        root.walkTopDown().filter { it.extension == "kt" }.forEach {
            assertTrue("${it.name} still uses the placeholder", !it.readText().contains("PlusComingSheet"))
        }
    }
}
