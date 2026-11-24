package com.masroufi.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.masroufi.app.data.repo.DebugSampleData
import com.masroufi.app.entitlements.Billing
import com.masroufi.app.entitlements.Entitlements
import com.masroufi.app.entitlements.StubBilling
import com.masroufi.app.notify.NotificationRouter
import com.masroufi.app.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @Inject lateinit var entitlements: Entitlements
    @Inject lateinit var sampleData: DebugSampleData
    @Inject lateinit var billing: Billing
    @Inject lateinit var router: NotificationRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        router.handle(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            AppRoot(entitlements, if (BuildConfig.DEBUG) sampleData else null, if (BuildConfig.DEBUG) billing as? StubBilling else null)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        router.handle(intent)
    }
}
