package com.masroufi.app.core

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/** This context in the app's chosen language even before Android 13 hands it to the application context (notifications, widgets). */
fun Context.inAppLanguage(): Context {
    val chosen = AppCompatDelegate.getApplicationLocales()
    if (chosen.isEmpty) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(chosen[0]) })
}
