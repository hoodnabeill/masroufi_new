package com.masroufi.app

import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.feature.gallery.GalleryScreen
import com.masroufi.app.ui.theme.MasroufiTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var entitlements: Entitlements

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            MasroufiTheme {
                // shortcut: gallery is the start screen until Phase 3 builds Home; replace then.
                GalleryScreen(entitlements)
            }
        }
    }
}
