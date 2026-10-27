@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.masroufi.app.ui.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.masroufi.app.ui.theme.LocalReduceMotion

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Container transform between a card and its detail screen: both sides use the same [key], and the
 * bounds morph during the navigation transition. Falls back to no-op outside a shared scope or with reduced motion.
 */
@Composable
fun Modifier.sharedContainer(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalNavAnimatedScope.current ?: return this
    if (LocalReduceMotion.current) return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}
