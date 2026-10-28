package com.masroufi.app.feature.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.masroufi.app.R
import com.masroufi.app.security.AppLockManager
import com.masroufi.app.security.PinResult
import com.masroufi.app.ui.components.AuroraBackground
import com.masroufi.app.ui.components.IconBadge
import com.masroufi.app.ui.components.glass
import com.masroufi.app.ui.theme.LocalGlass
import com.masroufi.app.ui.components.rememberHaptics
import com.masroufi.app.ui.theme.LocalReduceMotion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class LockViewModel @Inject constructor(val lockManager: AppLockManager) : ViewModel()

const val PIN_LENGTH = 4

fun canUseBiometric(activity: FragmentActivity): Boolean =
    BiometricManager.from(activity).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometricPrompt(activity: FragmentActivity, title: String, cancel: String, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
    )
}

@Composable
fun LockScreen(biometricEnabled: Boolean, viewModel: LockViewModel = hiltViewModel()) {
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val reduce = LocalReduceMotion.current
    var pin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val title = stringResource(R.string.lock_biometric_title)
    val cancel = stringResource(R.string.lock_use_pin)
    val wrongText = stringResource(R.string.lock_wrong_pin)
    val throttledText = stringResource(R.string.lock_throttled)
    val biometricAvailable = biometricEnabled && canUseBiometric(activity)

    fun biometric() = showBiometricPrompt(activity, title, cancel) { viewModel.lockManager.unlockedByBiometric() }

    LaunchedEffect(Unit) { if (biometricAvailable) biometric() }

    fun submit(value: String) {
        scope.launch {
            when (val result = viewModel.lockManager.verifyPin(value)) {
                PinResult.Success -> haptics.confirm()
                is PinResult.Wrong -> {
                    haptics.reject()
                    message = wrongText.format(result.attemptsLeft)
                    pin = ""
                    if (!reduce) {
                        for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x)
                    }
                }
                is PinResult.Throttled -> {
                    haptics.reject()
                    message = throttledText.format(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(result.retryAtMillis)))
                    pin = ""
                }
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = LocalGlass.current.base) {
        AuroraBackground(layout = 2)
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            IconBadge(Icons.Outlined.Lock, MaterialTheme.colorScheme.primary, size = 72.dp)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier
                    .offset { IntOffset(shake.value.roundToInt(), 0) }
                    .semantics { contentDescription = "${pin.length} / $PIN_LENGTH" },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (index < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            PinPad(
                onDigit = { digit ->
                    if (pin.length < PIN_LENGTH) {
                        haptics.tick()
                        pin += digit
                        if (pin.length == PIN_LENGTH) submit(pin)
                    }
                },
                onBackspace = { pin = pin.dropLast(1) },
                extra = if (biometricAvailable) {
                    {
                        IconButton(onClick = { biometric() }, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Outlined.Fingerprint, contentDescription = stringResource(R.string.lock_biometric_title), modifier = Modifier.size(32.dp))
                        }
                    }
                } else {
                    null
                },
            )
        }
    }
}

/** Digits keep LTR order in both languages, matching every phone dial pad Egyptian users know. */
@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, extra: (@Composable () -> Unit)?) {
    val rows = listOf("123", "456", "789")
    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) { row.forEach { PinKey(it.toString()) { onDigit(it) } } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) { extra?.invoke() }
                PinKey("0") { onDigit('0') }
                IconButton(onClick = onBackspace, modifier = Modifier.size(72.dp)) {
                    Icon(Icons.Outlined.Backspace, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(74.dp).glass(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}
