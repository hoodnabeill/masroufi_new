package com.masroufi.app.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** The app's language switch behind a seam, so Settings' tests can see the call without recreating an activity. */
interface AppLocales {
    /** Sets the app language ("ar" or "en"); the activity recreates in it. */
    fun set(languageTag: String)
}

class AndroidAppLocales : AppLocales {
    override fun set(languageTag: String) = AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
}
