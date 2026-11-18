package com.masroufi.app.ui.components

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

/** The plural string for [count] with the count as its one argument (Arabic has six forms). */
@Composable
fun pluralText(@PluralsRes id: Int, count: Int): String {
    LocalConfiguration.current // a language change re-reads the resource
    return LocalContext.current.resources.getQuantityString(id, count, count)
}
