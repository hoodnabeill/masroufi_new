package com.masroufi.app.feature.story

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The tap halves are out of TalkBack's reach: the slide counter offers previous and next as accessibility actions. */
@RunWith(AndroidJUnit4::class)
class StoryAccessibilityTest {
    @get:Rule val rule = createComposeRule()

    @Test fun accessibilityActionsMoveTheSlides() {
        var next = 0
        var back = 0
        val actions = object : StoryActions {
            override fun next() { next++ }
            override fun back() { back++ }
        }
        val payload = fullPayload()
        val ui = StoryUi(loading = false, story = story(payload), slides = storySlides(payload), index = 1)
        rule.setContent { MasroufiTheme { StoryContent(ui, mutableStateOf(0f), actions, {}, { _, _ -> }) } }
        val custom = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(2, custom.size)
        rule.runOnUiThread { custom[0].action() }
        assertEquals(listOf(0, 1), listOf(next, back))
        rule.runOnUiThread { custom[1].action() }
        assertEquals(listOf(1, 1), listOf(next, back))
    }
}
