package com.masroufi.app.feature.gallery

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
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

    // DIAG temporary: AssertionError -> IllegalStateException, other -> IllegalArgumentException; the line shows the step.
    @Test
    fun galleryComposes() {
        try { rule.setContent { MasroufiTheme { GalleryScreen() } } }
        catch (t: AssertionError) { throw IllegalStateException() }
        catch (t: Throwable) { throw IllegalArgumentException(t.javaClass.simpleName) }
        try { rule.onNodeWithText("Icons").assertExists() }
        catch (t: AssertionError) { throw IllegalStateException() }
        catch (t: Throwable) { throw IllegalArgumentException(t.javaClass.simpleName) }
        try { rule.onNode(hasScrollAction()).performScrollToNode(hasText("QershBubble")) }
        catch (t: AssertionError) { throw IllegalStateException() }
        catch (t: Throwable) { throw IllegalArgumentException(t.javaClass.simpleName) }
        try { rule.onNodeWithText("QershBubble").assertExists() }
        catch (t: AssertionError) { throw IllegalStateException() }
        catch (t: Throwable) { throw IllegalArgumentException(t.javaClass.simpleName) }
    }
}
