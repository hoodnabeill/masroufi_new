package com.masroufi.app.feature.kids

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** A strong fingerprint or face, or the screen lock where the system allows combining them (API 30+; below it, biometrics only). */
private fun authenticators(): Int = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_STRONG or DEVICE_CREDENTIAL else BIOMETRIC_STRONG

internal fun canAuthenticateParent(context: Context): Boolean =
    BiometricManager.from(context).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS

internal fun Context.fragmentActivity(): FragmentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** The system prompt; [onSuccess] runs only when the system says the parent proved themself (the app never sees biometric data). */
internal fun promptParent(activity: FragmentActivity, title: String, cancel: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setAllowedAuthenticators(authenticators())
    // With the device credential allowed the system supplies the cancel button; without it one is required.
    if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText(cancel)
    prompt.authenticate(info.build())
}
