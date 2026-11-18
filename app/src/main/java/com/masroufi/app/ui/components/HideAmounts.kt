package com.masroufi.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.masroufi.app.core.formatPounds

/** The "hide amounts" setting (Task 55): read by the shared money composables, provided once in AppRoot. */
val LocalHideAmounts = compositionLocalOf { false }

/** What an amount becomes while amounts are hidden. */
const val HIDDEN_AMOUNT = "•••"

/** [formatPounds] for text on screen: dots while amounts are hidden. Never for a field the user is typing in, shared text or saved text. */
@Composable
fun poundsText(piastres: Long): String = if (LocalHideAmounts.current) HIDDEN_AMOUNT else formatPounds(piastres)
