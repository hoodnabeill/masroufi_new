package com.masroufi.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.masroufi.app.data.prefs.SettingsRepository
import com.masroufi.app.ui.MasroufiRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** AppCompatActivity (a FragmentActivity) is required by BiometricPrompt and per-app language switching. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Secure by default until settings load, so the recents thumbnail never flashes balances.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                settings.settings.map { it.hideInRecents }.distinctUntilChanged().collect { hide ->
                    if (hide) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }
        setContent { MasroufiRoot() }
    }
}
