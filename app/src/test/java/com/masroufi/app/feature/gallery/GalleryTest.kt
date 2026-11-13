package com.masroufi.app.feature.gallery

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.masroufi.app.ui.theme.MasroufiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ar")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GalleryTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun galleryComposes() {
        rule.setContent { MasroufiTheme { GalleryScreen() } }
        rule.onNodeWithText("Icons").assertExists()
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("QershBubble"))
        rule.onNodeWithText("QershBubble").assertExists()
    }
}
